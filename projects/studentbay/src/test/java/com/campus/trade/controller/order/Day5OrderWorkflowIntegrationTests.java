package com.campus.trade.controller.order;

import com.campus.trade.common.BusinessException;
import com.campus.trade.dto.order.*;
import com.campus.trade.entity.cart.CartItem;
import com.campus.trade.entity.chat.ChatMessage;
import com.campus.trade.entity.chat.Conversation;
import com.campus.trade.entity.order.*;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.cart.CartRepository;
import com.campus.trade.repository.chat.ChatMessageRepository;
import com.campus.trade.repository.chat.ConversationRepository;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.order.AfterSaleService;
import com.campus.trade.service.order.OrderService;
import com.campus.trade.service.order.PickupMeetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Day5OrderWorkflowIntegrationTests {
    @Autowired private MockMvc web;
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;
    @Autowired private CartRepository cart;
    @Autowired private ConversationRepository conversations;
    @Autowired private ChatMessageRepository messages;
    @Autowired private OrderRepository orders;
    @Autowired private OrderService orderService;
    @Autowired private AfterSaleService afterSales;
    @Autowired private PickupMeetingService pickupMeetings;

    @Test
    void returnRequestShouldNotifySellerAndKeepBothViewsSynchronized() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        Product product = productForBuyer(buyer.getId(), List.of());
        OrderResponse created = pickupOrder(buyer, product);
        AppUser seller = users.findById(created.sellerId()).orElseThrow();
        orderService.pay(created.id(), buyer.getId(), "WECHAT_PAY", "sync-after-sale-payment");

        web.perform(post("/api/order/{id}/return-request", created.id()).session(session(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"商品与描述不符\",\"description\":\"同步测试\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"));

        for (MockHttpSession participant : List.of(session(buyer), session(seller))) {
            web.perform(get("/api/order/{id}", created.id()).session(participant))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.shippingMethod").value("PICKUP"))
                    .andExpect(jsonPath("$.data.status").value("RETURN_REQUESTED"));
        }
        web.perform(get("/api/order/mine/seller").session(session(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(created.id()))
                .andExpect(jsonPath("$.data[0].shippingMethod").value("PICKUP"))
                .andExpect(jsonPath("$.data[0].status").value("RETURN_REQUESTED"));

        Conversation conversation = conversations.findByProductIdAndBuyerId(product.getId(), buyer.getId()).orElseThrow();
        List<ChatMessage> events = messages.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        assertThat(events.get(events.size() - 1).getContent()).startsWith("[[ORDER_EVENT:AFTER_SALE_REQUESTED:");
        assertThat(events.get(events.size() - 1).getReadAt()).isNull();
        web.perform(get("/api/chat/unread-count").session(session(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(greaterThanOrEqualTo(1)));

        web.perform(post("/api/order/{id}/courier-booking", created.id()).session(session(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierCompany\":\"顺丰速运\",\"pickupTime\":\"今天 18:00\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void paymentShouldReturnSameResultForSameIdempotencyKeyAndRejectAnotherKey() {
        AppUser buyer = user("lin@campus.edu");
        OrderResponse created = pickupOrder(buyer, productForBuyer(buyer.getId(), List.of()));

        OrderResponse first = orderService.pay(created.id(), buyer.getId(), "WECHAT_PAY", "day5-pay-key-1");
        OrderResponse repeated = orderService.pay(created.id(), buyer.getId(), "WECHAT_PAY", "day5-pay-key-1");

        assertThat(repeated.status()).isEqualTo(OrderStatus.PAID);
        assertThat(repeated.paidAt()).isEqualTo(first.paidAt());
        assertThatThrownBy(() -> orderService.pay(created.id(), buyer.getId(), "WECHAT_PAY", "day5-pay-key-2"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void paymentEndpointShouldRequireAKeyAndRemainIdempotentOverHttp() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        OrderResponse created = pickupOrder(buyer, productForBuyer(buyer.getId(), List.of()));
        MockHttpSession session = session(buyer);

        web.perform(post("/api/order/{id}/payment", created.id()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"ALIPAY\"}"))
                .andExpect(status().isBadRequest());

        String body = "{\"paymentMethod\":\"ALIPAY\",\"idempotencyKey\":\"http-stable-key\"}";
        web.perform(post("/api/order/{id}/payment", created.id()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));
        web.perform(post("/api/order/{id}/payment", created.id()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    void timeoutTaskShouldCancelOrderAndReleaseReservedProduct() {
        AppUser buyer = user("lin@campus.edu");
        Product product = productForBuyer(buyer.getId(), List.of());
        OrderResponse created = pickupOrder(buyer, product);
        TradeOrder stored = orders.findById(created.id()).orElseThrow();
        stored.setCreatedAt(LocalDateTime.now().minusHours(2));
        stored.setPaymentExpiresAt(LocalDateTime.now().minusMinutes(90));
        orders.saveAndFlush(stored);

        int cancelled = orderService.cancelExpiredUnpaidOrders(LocalDateTime.now().minusMinutes(30));

        TradeOrder result = orders.findById(created.id()).orElseThrow();
        assertThat(cancelled).isGreaterThanOrEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getCancellationReason()).contains("支付超时");
        assertThat(result.getCancelledAt()).isNotNull();
        assertThat(products.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.AVAILABLE);
    }

    @Test
    void buyerShouldCancelUnpaidOrderAndReleaseProductWhileSellerCannotCancel() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        Product product = productForBuyer(buyer.getId(), List.of());
        OrderResponse created = pickupOrder(buyer, product);
        AppUser seller = users.findById(created.sellerId()).orElseThrow();

        web.perform(post("/api/order/{id}/cancel", created.id()).session(session(seller)))
                .andExpect(status().isForbidden());
        web.perform(post("/api/order/{id}/cancel", created.id()).session(session(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationReason").value("买家主动取消未付款订单"));

        assertThat(products.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.AVAILABLE);
    }

    @Test
    void sellerShouldAdjustProductAndShippingPriceOnlyBeforePayment() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        OrderResponse created = pickupOrder(buyer, productForBuyer(buyer.getId(), List.of()));
        AppUser seller = users.findById(created.sellerId()).orElseThrow();
        String adjustment = "{\"productAmount\":75.00,\"shippingCost\":3.50}";

        web.perform(patch("/api/order/{id}/price-adjustment", created.id()).session(session(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content(adjustment))
                .andExpect(status().isForbidden());
        web.perform(patch("/api/order/{id}/price-adjustment", created.id()).session(session(seller))
                        .contentType(MediaType.APPLICATION_JSON).content(adjustment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productAmount").value(75.00))
                .andExpect(jsonPath("$.data.shippingCost").value(3.50))
                .andExpect(jsonPath("$.data.totalAmount").value(78.50))
                .andExpect(jsonPath("$.data.priceAdjustedAt").isNotEmpty());

        orderService.pay(created.id(), buyer.getId(), "WECHAT_PAY", "price-locked-after-payment");
        assertThatThrownBy(() -> orderService.adjustPendingPrice(created.id(), seller.getId(),
                new AdjustOrderPriceRequest(new BigDecimal("60.00"), BigDecimal.ZERO)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void paidOrderShouldAllowImmediateRefundButCloseSevenDaysAfterReceipt() {
        AppUser buyer = user("lin@campus.edu");
        OrderResponse immediate = pickupOrder(buyer, productForBuyer(buyer.getId(), List.of()));
        orderService.pay(immediate.id(), buyer.getId(), "WECHAT_PAY", "immediate-refund");

        AfterSaleTicket ticket = afterSales.requestReturn(immediate.id(), buyer.getId(),
                new ReturnRequest("不再需要，申请退款", "付款后立即申请"));
        assertThat(ticket.getStatus()).isEqualTo(AfterSaleStatus.REQUESTED);

        Product another = productForBuyer(buyer.getId(), List.of());
        OrderResponse expired = pickupOrder(buyer, another);
        orderService.pay(expired.id(), buyer.getId(), "WECHAT_PAY", "expired-refund");
        TradeOrder stored = orders.findById(expired.id()).orElseThrow();
        stored.setStatus(OrderStatus.COMPLETED);
        stored.setReceivedAt(LocalDateTime.now().minusDays(8));
        orders.saveAndFlush(stored);

        assertThatThrownBy(() -> afterSales.requestReturn(expired.id(), buyer.getId(),
                new ReturnRequest("商品存在质量问题", "已经超过售后期限")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getMessage()).contains("超过 7 天");
                });
    }

    @Test
    void chatConversationShouldExposeLinkedProductAndRoleSpecificOrderActions() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        Product product = productForBuyer(buyer.getId(), List.of());
        OrderResponse created = pickupOrder(buyer, product);
        AppUser seller = users.findById(created.sellerId()).orElseThrow();

        web.perform(post("/api/chat/product/{id}", product.getId()).session(session(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productStatus").value("RESERVED"))
                .andExpect(jsonPath("$.data.order.id").value(created.id()))
                .andExpect(jsonPath("$.data.order.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.order.canCancel").value(true))
                .andExpect(jsonPath("$.data.order.canAdjustPrice").value(false));

        web.perform(get("/api/chat/conversations").param("box", "SELLER").session(session(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sellerView").value(true))
                .andExpect(jsonPath("$.data[0].order.canCancel").value(false))
                .andExpect(jsonPath("$.data[0].order.canAdjustPrice").value(true));
    }

    @Test
    void pickupProposalShouldWarnAboutConflictAndRequireCounterpartyAcceptance() {
        AppUser buyer = user("lin@campus.edu");
        Product firstProduct = productForBuyer(buyer.getId(), List.of());
        Product secondProduct = productForBuyer(buyer.getId(), List.of(firstProduct.getSellerId()));
        OrderResponse first = pickupOrder(buyer, firstProduct);
        pickupOrder(buyer, secondProduct);
        LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.of(17, 45));

        PickupMeetingProposal proposal = pickupMeetings.propose(first.id(), buyer.getId(),
                new PickupMeetingProposalRequest(start, start.plusMinutes(30), "体育馆东门"));

        assertThat(proposal.getConflictWarning()).contains("时间冲突提醒");
        assertThatThrownBy(() -> pickupMeetings.respond(first.id(), proposal.getId(), buyer.getId(),
                new PickupProposalDecisionRequest("ACCEPT"))).isInstanceOf(BusinessException.class);
        PickupMeetingProposal accepted = pickupMeetings.respond(first.id(), proposal.getId(), first.sellerId(),
                new PickupProposalDecisionRequest("ACCEPT"));
        assertThat(accepted.getStatus()).isEqualTo(PickupProposalStatus.ACCEPTED);
        assertThat(orders.findById(first.id()).orElseThrow().getPickupLocation()).isEqualTo("体育馆东门");
    }

    @Test
    void rejectedReturnCanBecomeInterventionAndCompleteReviewedRefund() {
        AppUser buyer = user("lin@campus.edu");
        Product product = productForBuyer(buyer.getId(), List.of());
        OrderResponse created = pickupOrder(buyer, product);
        orderService.pay(created.id(), buyer.getId(), "WECHAT_PAY", "after-sale-payment");
        TradeOrder delivered = orders.findById(created.id()).orElseThrow();
        delivered.setStatus(OrderStatus.COMPLETED);
        products.findById(product.getId()).ifPresent(item -> { item.setStatus(ProductStatus.SOLD); products.save(item); });
        orders.saveAndFlush(delivered);

        AfterSaleTicket requested = afterSales.requestReturn(created.id(), buyer.getId(),
                new ReturnRequest("商品与描述不符", "实物有未说明的损坏"));
        AfterSaleTicket rejected = afterSales.sellerReview(created.id(), created.sellerId(),
                new AfterSaleReviewRequest("REJECT", "交付时已验货"));
        AfterSaleTicket intervention = afterSales.requestIntervention(created.id(), buyer.getId(),
                new InterventionRequest("双方对验货结果存在分歧"));
        AfterSaleTicket approved = afterSales.adminReview(requested.getId(),
                new AfterSaleReviewRequest("APPROVE", "平台复核证据后同意退款"));
        AfterSaleTicket refunded = afterSales.executeRefund(approved.getId());

        assertThat(rejected.getSellerReviewedAt()).isNotNull();
        assertThat(intervention.getInterventionAt()).isNotNull();
        assertThat(refunded.getStatus()).isEqualTo(AfterSaleStatus.REFUNDED);
        assertThat(orders.findById(created.id()).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(orders.findById(created.id()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(products.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.AVAILABLE);
    }

    @Test
    void adminDashboardShouldFilterDatesExposeCategoriesAndProtectAdminRoutes() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        AppUser admin = user("admin@studentbay.local");
        OrderResponse created = pickupOrder(buyer, productForBuyer(buyer.getId(), List.of()));
        orderService.pay(created.id(), buyer.getId(), "WECHAT_PAY", "analytics-payment");
        String today = LocalDate.now().toString();

        web.perform(get("/api/admin/dashboard").session(session(admin))
                        .param("from", today).param("to", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range.from").value(today))
                .andExpect(jsonPath("$.data.dailyTrend.length()").value(1))
                .andExpect(jsonPath("$.data.categoryAnalysis[0].category").isNotEmpty())
                .andExpect(jsonPath("$.data.categoryAnalysis[0].amount").isNumber());
        web.perform(get("/api/admin/dashboard").session(session(admin))
                        .param("from", today).param("to", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isBadRequest());
        web.perform(get("/api/admin/after-sales").session(session(buyer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointsShouldReviewAndExecuteApprovedRefund() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        AppUser admin = user("admin@studentbay.local");
        Product product = productForBuyer(buyer.getId(), List.of());
        OrderResponse created = pickupOrder(buyer, product);
        orderService.pay(created.id(), buyer.getId(), "WECHAT_PAY", "admin-refund-payment");
        TradeOrder delivered = orders.findById(created.id()).orElseThrow();
        delivered.setStatus(OrderStatus.SHIPPED);
        orders.saveAndFlush(delivered);
        AfterSaleTicket ticket = afterSales.requestReturn(created.id(), buyer.getId(),
                new ReturnRequest("商品存在质量问题", "自动化退款审核场景"));
        afterSales.sellerReview(created.id(), created.sellerId(),
                new AfterSaleReviewRequest("APPROVE", "同意退货"));

        web.perform(get("/api/admin/after-sales").session(session(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].workOrderNo").value(ticket.getWorkOrderNo()));
        web.perform(post("/api/admin/after-sales/{id}/review", ticket.getId()).session(session(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"note\":\"证据充分，同意退款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUND_APPROVED"));
        web.perform(post("/api/admin/after-sales/{id}/refund", ticket.getId()).session(session(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
        assertThat(orders.findById(created.id()).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    private OrderResponse pickupOrder(AppUser buyer, Product product) {
        CartItem item = new CartItem();
        item.setUserId(buyer.getId());
        item.setProductId(product.getId());
        item.setQuantity(1);
        Long cartId = cart.save(item).getId();
        return orderService.checkout(buyer.getId(), new CheckoutRequest(List.of(cartId), null, "PICKUP",
                null, "WECHAT_PAY", false, LocalDate.now(), LocalTime.of(17, 30),
                LocalTime.of(18, 0), "图书馆北门")).get(0);
    }

    private Product productForBuyer(Long buyerId, List<Long> excludedSellers) {
        return products.findAll().stream()
                .filter(product -> product.getStatus() == ProductStatus.AVAILABLE)
                .filter(product -> !product.getSellerId().equals(buyerId))
                .filter(product -> !excludedSellers.contains(product.getSellerId()))
                .findFirst().orElseThrow();
    }

    private AppUser user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }

    private MockHttpSession session(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());
        return session;
    }
}
