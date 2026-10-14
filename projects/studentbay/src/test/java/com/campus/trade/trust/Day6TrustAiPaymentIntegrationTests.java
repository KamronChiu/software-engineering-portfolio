package com.campus.trade.trust;

import com.campus.trade.dto.order.AdjustOrderPriceRequest;
import com.campus.trade.dto.order.CheckoutRequest;
import com.campus.trade.dto.order.OrderResponse;
import com.campus.trade.entity.cart.CartItem;
import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.cart.CartRepository;
import com.campus.trade.repository.product.ProductRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Day6TrustAiPaymentIntegrationTests {
    @Autowired private MockMvc web;
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;
    @Autowired private CartRepository cart;
    @Autowired private OrderService orders;

    @Test
    void naturalLanguageSearchShouldUnderstandBudgetProductAndPickupIntentWithoutNetworkAi() throws Exception {
        web.perform(post("/api/products/natural-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"预算150元，想买适合宿舍用的安静键盘，最好能面交\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("LOCAL_SEMANTIC"))
                .andExpect(jsonPath("$.data.interpretation.maxPrice").value(150))
                .andExpect(jsonPath("$.data.interpretation.category").value("数码产品"))
                .andExpect(jsonPath("$.data.interpretation.preferPickup").value(true))
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.products[0].price").value(org.hamcrest.Matchers.lessThanOrEqualTo(150.0)));
    }

    @Test
    void naturalLanguageSearchShouldExpandToySynonymsAndNeverFillResultsWithUnrelatedOtherItems() throws Exception {
        web.perform(post("/api/products/natural-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"预算100元，想买儿童玩具\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interpretation.maxPrice").value(100))
                .andExpect(jsonPath("$.data.interpretation.category").value("其他"))
                .andExpect(jsonPath("$.data.interpretation.keywords",
                        org.hamcrest.Matchers.hasItems("玩具", "积木", "拼图", "玩偶", "遥控车")))
                .andExpect(jsonPath("$.data.products.length()", org.hamcrest.Matchers.greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.data.products[*].title",
                        org.hamcrest.Matchers.hasItems("儿童木质积木套装", "动物世界儿童拼图", "遥控越野玩具车")))
                .andExpect(jsonPath("$.data.products[*].title",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("雨伞")))))
                .andExpect(jsonPath("$.data.products[*].price",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.lessThanOrEqualTo(100.0))));
    }

    @Test
    void completedOrderRatingShouldAffectTrustScoreAndRejectDuplicateRating() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        Product product = availableProduct(buyer.getId());
        OrderResponse order = checkout(buyer, product);
        orders.pay(order.id(), buyer.getId(), "WECHAT_PAY", "trust-test-payment");
        orders.ship(order.id());
        orders.receive(order.id());

        web.perform(post("/api/trust/orders/{id}/rating", order.id()).session(session(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5,\"comment\":\"沟通顺畅，商品符合描述\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetUserId").value(product.getSellerId()))
                .andExpect(jsonPath("$.data.score").value(5));

        web.perform(post("/api/trust/orders/{id}/rating", order.id()).session(session(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":4}"))
                .andExpect(status().isConflict());

        web.perform(get("/api/trust/users/{id}", product.getSellerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ratingCount").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(5.0))
                .andExpect(jsonPath("$.data.factors").isArray());
    }

    @Test
    void highValueAndRepeatedLargePriceChangesShouldCreateExplainableRiskAlerts() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        Product product = product("答辩测试高价值平板", "900.00", buyer.getId());
        OrderResponse order = checkout(buyer, product);
        Long sellerId = product.getSellerId();
        orders.adjustPendingPrice(order.id(), sellerId,
                new AdjustOrderPriceRequest(new BigDecimal("650.00"), new BigDecimal("20.00")));
        orders.adjustPendingPrice(order.id(), sellerId,
                new AdjustOrderPriceRequest(new BigDecimal("780.00"), new BigDecimal("20.00")));

        web.perform(get("/api/risk/orders/{id}", order.id()).session(session(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.alerts[*].code", org.hamcrest.Matchers.hasItems("HIGH_VALUE", "ABNORMAL_PRICE_CHANGE")));
    }

    @Test
    void sandboxStartShouldStayDisabledUntilSecretsAreConfigured() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        web.perform(get("/api/payments/alipay/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        web.perform(get("/api/payments/alipay/start/{id}", 1).session(session(buyer)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("尚未配置")));
    }

    private OrderResponse checkout(AppUser buyer, Product product) {
        CartItem item = new CartItem();
        item.setUserId(buyer.getId());
        item.setProductId(product.getId());
        item.setQuantity(1);
        Long cartId = cart.save(item).getId();
        return orders.checkout(buyer.getId(), new CheckoutRequest(List.of(cartId), null, "PICKUP",
                null, "WECHAT_PAY", false, LocalDate.now(), LocalTime.of(17, 30),
                LocalTime.of(18, 0), "图书馆北门")).get(0);
    }

    private Product availableProduct(Long buyerId) {
        return products.findByStatusOrderByCreatedAtDesc(ProductStatus.AVAILABLE).stream()
                .filter(product -> !product.getSellerId().equals(buyerId)).findFirst().orElseThrow();
    }

    private Product product(String title, String price, Long buyerId) {
        Long sellerId = users.findAll().stream().map(AppUser::getId)
                .filter(id -> !id.equals(buyerId)).findFirst().orElseThrow();
        Product product = new Product();
        product.setSellerId(sellerId);
        product.setTitle(title);
        product.setDescription("用于风险模块自动化测试的商品");
        product.setCategory("数码产品");
        product.setCondition("九成新");
        product.setPrice(new BigDecimal(price));
        product.setOriginalPrice(new BigDecimal("1200.00"));
        product.setLocationName("图书馆北门");
        product.setStatus(ProductStatus.AVAILABLE);
        return products.save(product);
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
