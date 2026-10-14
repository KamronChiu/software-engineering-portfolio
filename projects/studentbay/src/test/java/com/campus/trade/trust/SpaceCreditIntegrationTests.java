package com.campus.trade.trust;

import com.campus.trade.dto.order.CheckoutRequest;
import com.campus.trade.entity.cart.CartItem;
import com.campus.trade.entity.review.ReviewType;
import com.campus.trade.entity.review.TradeReview;
import com.campus.trade.entity.social.Report;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.entity.user.UserRole;
import com.campus.trade.repository.cart.CartRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.review.TradeReviewRepository;
import com.campus.trade.repository.social.ReportRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.order.OrderService;
import com.campus.trade.service.trust.SpaceCreditService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖暂存中 [Day8] 我的空间信用体系（SpaceCreditService）：
 * 信用分 = 50 + 成功交易×6(≤+30) + 好评×6(≤+30) − 商品被举报×12(≤−36)，范围 0-100；
 * 买家 / 卖家信用分开评定，好评口径按评价方向（BUYER_REVIEWS_SELLER / SELLER_REVIEWS_BUYER）取值。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpaceCreditIntegrationTests {

    @Autowired
    private MockMvc web;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private TradeReviewRepository tradeReviews;

    @Autowired
    private ReportRepository reports;

    @Autowired
    private OrderService orderService;

    @Autowired
    private SpaceCreditService spaceCreditService;

    private static String unique() {
        return "credit-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private MockHttpSession sessionOf(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());
        return session;
    }

    private AppUser newUser(String tag) {
        AppUser user = new AppUser();
        user.setEmail(tag + unique() + "@campus.edu");
        user.setDisplayName(tag);
        user.setPasswordHash("unused");
        user.setRole(UserRole.USER);
        user.setStudentVerified(false);
        return users.save(user);
    }

    /** 卖家（session）发布一个可售商品，返回商品 id。 */
    private long publish(AppUser seller) throws Exception {
        MvcResult result = web.perform(post("/api/products").session(sessionOf(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"信用测试商品 %s","description":"用于信用口径验证","category":"教材资料",
                                 "condition":"全新","price":66.00,"originalPrice":99.00,
                                 "imageUrl":"https://example.com/credit.jpg","locationName":"图书馆北门",
                                 "latitude":23.1649870,"longitude":113.4461120}
                                """.formatted(unique())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    /** 买家买下商品并走完支付 / 发货 / 收货，订单进入 COMPLETED。 */
    private long completePurchase(AppUser buyer, long productId) throws Exception {
        CartItem item = new CartItem();
        item.setUserId(buyer.getId());
        item.setProductId(productId);
        item.setQuantity(1);
        Long cartItemId = cartRepository.save(item).getId();

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), null, "PICKUP", null,
                "WECHAT_PAY", false, LocalDate.now(), LocalTime.of(17, 30), LocalTime.of(18, 0),
                "信用测试面交点");
        long orderId = orderService.checkout(buyer.getId(), request).get(0).id();
        orderService.pay(orderId, buyer.getId(), "WECHAT_PAY", "credit-key-" + orderId);
        web.perform(patch("/api/order/{id}/ship", orderId)).andExpect(status().isOk());
        web.perform(patch("/api/order/{id}/receive", orderId)).andExpect(status().isOk());
        return orderId;
    }

    private Map<String, Object> credit(Long userId) {
        return spaceCreditService.credit(userId);
    }

    @Test
    void freshAccountShouldStartAtBaselineFiftyWithEmptyMetrics() {
        AppUser user = newUser("新手");
        Map<String, Object> result = credit(user.getId());

        assertThat(result.get("sellerCredit")).isEqualTo(50);
        assertThat(result.get("buyerCredit")).isEqualTo(50);
        assertThat(result.get("sellerLevel")).isEqualTo("建议谨慎交易");
        assertThat(result.get("buyerLevel")).isEqualTo("建议谨慎交易");
        assertThat(result.get("soldCount")).isEqualTo(0L);
        assertThat(result.get("boughtCount")).isEqualTo(0L);
        assertThat(result.get("sellerGoodReviews")).isEqualTo(0L);
        assertThat(result.get("buyerGoodReviews")).isEqualTo(0L);
        assertThat(result.get("reportedCount")).isEqualTo(0L);
    }

    @Test
    void completedPurchaseShouldRaiseBuyerCreditOnly() throws Exception {
        AppUser seller = newUser("卖家甲");
        AppUser buyer = newUser("买家甲");
        long productId = publish(seller);
        completePurchase(buyer, productId);

        Map<String, Object> result = credit(buyer.getId());
        assertThat(result.get("buyerCredit")).isEqualTo(56);
        assertThat(result.get("boughtCount")).isEqualTo(1L);
        assertThat(result.get("buyerLevel")).isEqualTo("信用正常");
        assertThat(result.get("sellerCredit")).isEqualTo(50);
        assertThat(result.get("soldCount")).isEqualTo(0L);
    }

    @Test
    void saleAndDirectionalGoodReviewShouldRaiseSellerCredit() throws Exception {
        AppUser seller = newUser("卖家乙");
        AppUser buyer = newUser("买家乙");
        long productId = publish(seller);
        long orderId = completePurchase(buyer, productId);

        // 买家 5 星好评卖家 → 卖家信用 +6，买家侧好评计数不受影响
        TradeReview review = new TradeReview();
        review.setOrderId(orderId);
        review.setProductId(productId);
        review.setReviewerId(buyer.getId());
        review.setRevieweeId(seller.getId());
        review.setReviewType(ReviewType.BUYER_REVIEWS_SELLER);
        review.setRating(5);
        review.setContent("卖家人很好");
        tradeReviews.save(review);

        Map<String, Object> result = credit(seller.getId());
        assertThat(result.get("sellerCredit")).isEqualTo(62);
        assertThat(result.get("soldCount")).isEqualTo(1L);
        assertThat(result.get("sellerGoodReviews")).isEqualTo(1L);
        assertThat(result.get("buyerGoodReviews")).isEqualTo(0L);
        assertThat(result.get("buyerCredit")).isEqualTo(50);
    }

    @Test
    void reportedProductsShouldDeductTwelvePerReport() throws Exception {
        AppUser seller = newUser("卖家丙");
        long productId = publish(seller);

        // 两条举报记录（同一商品）都计入扣分
        for (int i = 0; i < 2; i++) {
            Report report = new Report();
            report.setReporterId(newUser("举报人").getId());
            report.setProductId(productId);
            report.setReason("疑似虚假商品");
            reports.save(report);
        }

        Map<String, Object> result = credit(seller.getId());
        assertThat(result.get("reportedCount")).isEqualTo(2L);
        assertThat(result.get("sellerCredit")).isEqualTo(26);
        assertThat(result.get("sellerLevel")).isEqualTo("建议谨慎交易");
    }

    @Test
    void tradeAndPraiseBonusesShouldCapAtOneHundred() throws Exception {
        AppUser seller = newUser("卖家顶格");
        AppUser buyer = newUser("买家顶格");

        // 5 笔成交 + 5 条好评：50 + 30（交易封顶）+ 30（好评封顶）= 110 → 截断为 100
        for (int i = 0; i < 5; i++) {
            long productId = publish(seller);
            long orderId = completePurchase(buyer, productId);
            TradeReview review = new TradeReview();
            review.setOrderId(orderId);
            review.setProductId(productId);
            review.setReviewerId(buyer.getId());
            review.setRevieweeId(seller.getId());
            review.setReviewType(ReviewType.BUYER_REVIEWS_SELLER);
            review.setRating(5);
            review.setContent("连续好评");
            tradeReviews.save(review);
        }

        Map<String, Object> result = credit(seller.getId());
        assertThat(result.get("sellerCredit")).isEqualTo(100);
        assertThat(result.get("sellerLevel")).isEqualTo("非常可信");
        assertThat(result.get("soldCount")).isEqualTo(5L);
        assertThat(result.get("sellerGoodReviews")).isEqualTo(5L);
    }

    @Test
    void reportDeductionShouldCapAtThirtySix() throws Exception {
        AppUser seller = newUser("卖家地板");
        long productId = publish(seller);

        // 6 条举报：50 − 72 → 扣分封顶 −36 → 14（举报再多也不会低于 14）
        for (int i = 0; i < 6; i++) {
            Report report = new Report();
            report.setReporterId(newUser("举报人").getId());
            report.setProductId(productId);
            report.setReason("疑似虚假商品");
            reports.save(report);
        }

        Map<String, Object> result = credit(seller.getId());
        assertThat(result.get("sellerCredit")).isEqualTo(14);
        assertThat(result.get("sellerLevel")).isEqualTo("建议谨慎交易");

        // 再加 4 条举报仍保持扣分封顶不变
        for (int i = 0; i < 4; i++) {
            Report report = new Report();
            report.setReporterId(newUser("举报人").getId());
            report.setProductId(productId);
            report.setReason("疑似虚假商品");
            reports.save(report);
        }
        assertThat(credit(seller.getId()).get("sellerCredit")).isEqualTo(14);
    }

    @Test
    void creditEndpointShouldRequireLoginAndExposeScoreWithMetrics() throws Exception {
        web.perform(get("/api/space/credit"))
                .andExpect(status().isUnauthorized());

        AppUser user = newUser("接口用户");
        web.perform(get("/api/space/credit").session(sessionOf(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sellerCredit").value(50))
                .andExpect(jsonPath("$.data.buyerCredit").value(50))
                .andExpect(jsonPath("$.data.sellerLevel").value("建议谨慎交易"))
                .andExpect(jsonPath("$.data.soldCount").value(0))
                .andExpect(jsonPath("$.data.boughtCount").value(0))
                .andExpect(jsonPath("$.data.sellerGoodReviews").value(0))
                .andExpect(jsonPath("$.data.buyerGoodReviews").value(0))
                .andExpect(jsonPath("$.data.reportedCount").value(0))
                .andExpect(jsonPath("$.data.rule").isString());
    }
}
