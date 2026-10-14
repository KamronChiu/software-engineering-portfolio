package com.campus.trade.controller.order;

import com.campus.trade.dto.order.CheckoutRequest;
import com.campus.trade.dto.order.ConfirmShipmentRequest;
import com.campus.trade.dto.order.CourierBookingRequest;
import com.campus.trade.entity.cart.CartItem;
import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.order.PaymentStatus;
import com.campus.trade.entity.order.ShipmentStatus;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.repository.address.AddressRepository;
import com.campus.trade.repository.cart.CartRepository;
import com.campus.trade.repository.order.LogisticsEventRepository;
import com.campus.trade.repository.order.OrderItemRepository;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.order.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerIntegrationTests {

    @Autowired
    private MockMvc web;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private LogisticsEventRepository logisticsEventRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void cleanDatabase() {
        cartRepository.deleteAll();
        logisticsEventRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.findAll().forEach(product -> {
            product.setStatus(ProductStatus.AVAILABLE);
            productRepository.save(product);
        });
    }

    @Test
    void createOrderShouldPersistOneOrderAndOneItem() throws Exception {
        web.perform(post("/api/order/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1001,"sellerId":2,"productTitle":"Used Java textbook",
                                 "unitPrice":58.00,"quantity":2,"shippingMethod":"SHIPPING",
                                 "shippingAddress":"Dormitory 3, Room 201","shippingCost":5.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("order created")))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmount").value(121.00));
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(1);

        var savedOrder = orderRepository.findAll().get(0);
        assertThat(savedOrder.getBuyerId()).isEqualTo(1L);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(savedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("121.00");
    }

    @Test
    void orderShouldFollowPayShipReceiveLifecycle() throws Exception {
        web.perform(post("/api/order/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1002,"sellerId":2,"productTitle":"Second-hand bicycle",
                                 "unitPrice":300.00,"quantity":1,"shippingMethod":"PICKUP",
                                 "shippingAddress":"Campus gate","shippingCost":0}
                                """))
                .andExpect(status().isCreated());
        Long orderId = orderRepository.findAll().get(0).getId();

        web.perform(patch("/api/order/{id}/pay", orderId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));
        web.perform(patch("/api/order/{id}/ship", orderId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SHIPPED"));
        web.perform(patch("/api/order/{id}/receive", orderId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void checkoutShouldGroupItemsBySellerAndRequireTwoWayPickupCodes() {
        var buyer = userRepository.findByEmailIgnoreCase("lin@campus.edu").orElseThrow();
        var seller = userRepository.findByEmailIgnoreCase("chen@campus.edu").orElseThrow();
        var sellerProducts = productRepository.findBySellerIdOrderByCreatedAtDesc(seller.getId()).stream().limit(2).toList();
        assertThat(sellerProducts).hasSize(2);

        List<Long> cartItemIds = sellerProducts.stream().map(product -> {
            CartItem item = new CartItem();
            item.setUserId(buyer.getId());
            item.setProductId(product.getId());
            item.setQuantity(1);
            return cartRepository.save(item).getId();
        }).toList();

        CheckoutRequest request = new CheckoutRequest(cartItemIds, null, "PICKUP", null,
                "WECHAT_PAY", false, LocalDate.now(), LocalTime.of(17, 30), LocalTime.of(18, 0),
                "Library North Gate");
        var created = orderService.checkout(buyer.getId(), request);

        assertThat(created).hasSize(1);
        assertThat(created.get(0).items()).hasSize(2);
        assertThat(created.get(0).pickupLocation()).isEqualTo("Library North Gate");
        assertThat(created.get(0).myPickupCode()).hasSize(6);

        Long orderId = created.get(0).id();
        orderService.pay(orderId, buyer.getId(), "WECHAT_PAY");
        orderService.confirmPickupHandover(orderId, seller.getId(), created.get(0).myPickupCode());
        String sellerCode = orderService.getOrderForUser(orderId, seller.getId()).myPickupCode();
        var completed = orderService.receive(orderId, buyer.getId(), sellerCode);

        assertThat(completed.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderRepository.findById(orderId).orElseThrow().isSellerHandoverConfirmed()).isTrue();
        assertThat(orderRepository.findById(orderId).orElseThrow().isBuyerReceiptConfirmed()).isTrue();
    }

    @Test
    void checkoutShouldSplitOnePaymentIntoOrdersForDifferentSellers() {
        var buyer = userRepository.findByEmailIgnoreCase("lin@campus.edu").orElseThrow();
        var firstSeller = userRepository.findByEmailIgnoreCase("chen@campus.edu").orElseThrow();
        var secondSeller = userRepository.findByEmailIgnoreCase("wang@campus.edu").orElseThrow();
        var firstProduct = productRepository.findBySellerIdOrderByCreatedAtDesc(firstSeller.getId()).get(0);
        var secondProduct = productRepository.findBySellerIdOrderByCreatedAtDesc(secondSeller.getId()).get(0);

        List<Long> cartItemIds = List.of(firstProduct, secondProduct).stream().map(product -> {
            CartItem item = new CartItem();
            item.setUserId(buyer.getId());
            item.setProductId(product.getId());
            item.setQuantity(1);
            return cartRepository.save(item).getId();
        }).toList();

        var created = orderService.checkout(buyer.getId(), new CheckoutRequest(
                cartItemIds, null, "PICKUP", null, "WECHAT_PAY", false,
                LocalDate.now(), LocalTime.of(17, 30), LocalTime.of(18, 0), "Library North Gate"
        ));

        assertThat(created).hasSize(2);
        assertThat(created).extracting(order -> order.sellerId())
                .containsExactlyInAnyOrder(firstSeller.getId(), secondSeller.getId());
        assertThat(created).allSatisfy(order -> assertThat(order.items()).hasSize(1));
        assertThat(cartRepository.countByUserId(buyer.getId())).isZero();
    }

    @Test
    void deliveryOrderShouldCompleteCourierAndLogisticsLifecycle() {
        var buyer = userRepository.findByEmailIgnoreCase("lin@campus.edu").orElseThrow();
        var seller = userRepository.findByEmailIgnoreCase("chen@campus.edu").orElseThrow();
        var product = productRepository.findBySellerIdOrderByCreatedAtDesc(seller.getId()).get(0);
        var address = addressRepository.findByUserIdOrderByDefaultAddressDescIdDesc(buyer.getId()).get(0);
        CartItem cartItem = new CartItem();
        cartItem.setUserId(buyer.getId());
        cartItem.setProductId(product.getId());
        cartItem.setQuantity(1);
        Long cartItemId = cartRepository.save(cartItem).getId();

        var created = orderService.checkout(buyer.getId(), new CheckoutRequest(
                List.of(cartItemId), address.getId(), "DELIVERY", null, "ALIPAY", false,
                null, null, null, null
        )).get(0);
        orderService.pay(created.id(), buyer.getId(), "ALIPAY");
        var booked = orderService.bookCourier(created.id(), seller.getId(),
                new CourierBookingRequest("顺丰校园服务", "今天 18:30"));
        assertThat(booked.status()).isEqualTo(OrderStatus.COURIER_BOOKED);

        var shipped = orderService.confirmShipment(created.id(), seller.getId(),
                new ConfirmShipmentRequest("SF1234567890"));
        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shipped.shipmentStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);

        var refreshed = orderService.refreshLogistics(created.id(), buyer.getId());
        assertThat(refreshed.shipmentStatus()).isEqualTo(ShipmentStatus.AT_DESTINATION);
        assertThat(refreshed.events()).hasSizeGreaterThanOrEqualTo(3);

        var completed = orderService.receive(created.id(), buyer.getId(), null);
        assertThat(completed.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.SOLD);
    }
}
