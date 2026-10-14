package com.campus.trade.controller.review;

import com.campus.trade.dto.order.CheckoutRequest;
import com.campus.trade.entity.cart.CartItem;
import com.campus.trade.entity.chat.ChatMessage;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.cart.CartRepository;
import com.campus.trade.repository.chat.ChatMessageRepository;
import com.campus.trade.repository.chat.ConversationRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.order.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖 [Day4] 提交（1719232）双向评价体系：
 * 仅 COMPLETED 订单的买卖双方可评价、每人每单一次、方向与评价对象自动判定；
 * 评价通过聊天协议消息（[[REVIEW:星级]]）通知对方；订单评价仅双方可见，
 * 用户评价聚合（total / averageRating）公开可查。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewFlowIntegrationTests {

    @Autowired
    private MockMvc web;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private ChatMessageRepository messages;

    @Autowired
    private OrderService orderService;

    private static String unique() {
        return "rev-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private AppUser user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }

    private MockHttpSession sessionOf(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());
        return session;
    }

    /** 卖家发布一个可售商品，返回商品 id。 */
    private long publish(AppUser seller) throws Exception {
        MvcResult result = web.perform(post("/api/products").session(sessionOf(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"评价测试商品 %s","description":"评价链路验证","category":"教材资料",
                                 "condition":"九成新","price":39.00,"originalPrice":88.00,
                                 "imageUrl":"https://example.com/rev.jpg","locationName":"图书馆北门",
                                 "latitude":23.1649870,"longitude":113.4461120}
                                """.formatted(unique())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    /** 买家下单（默认不付款），返回订单 id。 */
    private long checkout(AppUser buyer, long productId) {
        CartItem item = new CartItem();
        item.setUserId(buyer.getId());
        item.setProductId(productId);
        item.setQuantity(1);
        Long cartItemId = cartRepository.save(item).getId();
        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), null, "PICKUP", null,
                "WECHAT_PAY", false, LocalDate.now(), LocalTime.of(17, 30), LocalTime.of(18, 0),
                "评价测试面交点");
        return orderService.checkout(buyer.getId(), request).get(0).id();
    }

    /** 支付并收货，订单进入 COMPLETED。 */
    private long completeOrder(AppUser buyer, long productId) throws Exception {
        long orderId = checkout(buyer, productId);
        orderService.pay(orderId, buyer.getId(), "WECHAT_PAY", "rev-key-" + orderId);
        web.perform(patch("/api/order/{id}/ship", orderId)).andExpect(status().isOk());
        web.perform(patch("/api/order/{id}/receive", orderId)).andExpect(status().isOk());
        return orderId;
    }

    private void reviewAs(MockHttpSession session, long orderId, int rating, String content) throws Exception {
        web.perform(post("/api/reviews").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":%d,"rating":%d,"content":"%s"}
                                """.formatted(orderId, rating, content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    void onlyCompletedOrderPartiesCanReviewOnceInTheirOwnDirection() throws Exception {
        AppUser seller = user("chen@campus.edu");
        AppUser buyer = user("lin@campus.edu");
        AppUser outsider = user("wang@campus.edu");
        long productId = publish(seller);
        long orderId = completeOrder(buyer, productId);

        // 买家评卖家 → 方向 BUYER_REVIEWS_SELLER，评价对象是卖家
        web.perform(post("/api/reviews").session(sessionOf(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":%d,"rating":5,"content":"教材很新"}
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.reviewType").value("BUYER_REVIEWS_SELLER"))
                .andExpect(jsonPath("$.data.reviewerId").value(buyer.getId()))
                .andExpect(jsonPath("$.data.revieweeId").value(seller.getId()))
                .andExpect(jsonPath("$.data.rating").value(5));

        // 同一买家重复评价同一订单 → 409
        web.perform(post("/api/reviews").session(sessionOf(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":%d,"rating":4,"content":"再评一次"}
                                """.formatted(orderId)))
                .andExpect(status().isConflict());

        // 卖家评买家 → 方向反过来，评价对象是买家
        web.perform(post("/api/reviews").session(sessionOf(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":%d,"rating":4,"content":"交易顺利"}
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.reviewType").value("SELLER_REVIEWS_BUYER"))
                .andExpect(jsonPath("$.data.reviewerId").value(seller.getId()))
                .andExpect(jsonPath("$.data.revieweeId").value(buyer.getId()));

        // 非交易方评价 → 403
        web.perform(post("/api/reviews").session(sessionOf(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":%d,"rating":5,"content":"围观评价"}
                                """.formatted(orderId)))
                .andExpect(status().isForbidden());

        // 未完成订单不可评价 → 400
        long pendingOrderId = checkout(buyer, publish(seller));
        web.perform(post("/api/reviews").session(sessionOf(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":%d,"rating":5,"content":"未完成也评"}
                                """.formatted(pendingOrderId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orderReviewsVisibleToPartiesOnlyWhileUserAggregationIsPublic() throws Exception {
        AppUser seller = user("chen@campus.edu");
        AppUser buyer = user("lin@campus.edu");
        AppUser outsider = user("wang@campus.edu");
        long productId = publish(seller);
        long orderId = completeOrder(buyer, productId);
        reviewAs(sessionOf(buyer), orderId, 5, "教材很新");
        reviewAs(sessionOf(seller), orderId, 4, "交易顺利");

        // 订单评价仅买卖双方可见
        web.perform(get("/api/reviews/order/{orderId}", orderId).session(sessionOf(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        web.perform(get("/api/reviews/order/{orderId}", orderId).session(sessionOf(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        web.perform(get("/api/reviews/order/{orderId}", orderId).session(sessionOf(outsider)))
                .andExpect(status().isForbidden());

        // 用户聚合：卖家收到的买家评价
        web.perform(get("/api/reviews/user/{userId}", seller.getId())
                        .param("type", "BUYER_REVIEWS_SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(5.0))
                .andExpect(jsonPath("$.data.reviews[0].content").value("教材很新"));

        // 用户聚合：买家收到的卖家评价
        web.perform(get("/api/reviews/user/{userId}", buyer.getId())
                        .param("type", "SELLER_REVIEWS_BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(4.0))
                .andExpect(jsonPath("$.data.reviews[0].reviewType").value("SELLER_REVIEWS_BUYER"));

        // 无效评价类型 → 400
        web.perform(get("/api/reviews/user/{userId}", buyer.getId())
                        .param("type", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewShouldNotifyCounterpartyThroughProtocolChatMessage() throws Exception {
        AppUser seller = user("chen@campus.edu");
        AppUser buyer = user("lin@campus.edu");
        long productId = publish(seller);
        long orderId = completeOrder(buyer, productId);

        reviewAs(sessionOf(buyer), orderId, 5, "教材很新");

        // 评价写入（或复用）买卖双方关于该商品的会话，并以 [[REVIEW:星级]] 协议消息通知对方
        Long conversationId = conversations.findByProductIdAndBuyerId(productId, buyer.getId())
                .orElseThrow().getId();
        List<ChatMessage> chatMessages = messages.findByConversationIdOrderByCreatedAtAsc(conversationId);
        ChatMessage last = chatMessages.get(chatMessages.size() - 1);
        assertThat(last.getSenderId()).isEqualTo(buyer.getId());
        assertThat(last.getContent()).startsWith("[[REVIEW:5]]买家评价｜★★★★★")
                .contains("教材很新");
    }

    @Test
    void orderViewShouldFlipReviewSubmittedFlagsAsEachSideReviews() throws Exception {
        AppUser seller = user("chen@campus.edu");
        AppUser buyer = user("lin@campus.edu");
        long productId = publish(seller);
        long orderId = completeOrder(buyer, productId);

        // 双方均未评价：买家视角两个标记都为 false
        web.perform(get("/api/order/{id}", orderId).session(sessionOf(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myReviewSubmitted").value(false))
                .andExpect(jsonPath("$.data.counterpartyReviewSubmitted").value(false));

        // 买家已评、卖家未评：买家视角只亮"我已评价"
        reviewAs(sessionOf(buyer), orderId, 5, "教材很新");
        web.perform(get("/api/order/{id}", orderId).session(sessionOf(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myReviewSubmitted").value(true))
                .andExpect(jsonPath("$.data.counterpartyReviewSubmitted").value(false));
        // 卖家视角：对方（买家）已评、自己未评
        web.perform(get("/api/order/{id}", orderId).session(sessionOf(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myReviewSubmitted").value(false))
                .andExpect(jsonPath("$.data.counterpartyReviewSubmitted").value(true));

        // 双方均评：两个视角的标记全部为 true
        reviewAs(sessionOf(seller), orderId, 4, "交易顺利");
        web.perform(get("/api/order/{id}", orderId).session(sessionOf(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myReviewSubmitted").value(true))
                .andExpect(jsonPath("$.data.counterpartyReviewSubmitted").value(true));
        web.perform(get("/api/order/{id}", orderId).session(sessionOf(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myReviewSubmitted").value(true))
                .andExpect(jsonPath("$.data.counterpartyReviewSubmitted").value(true));
    }
}
