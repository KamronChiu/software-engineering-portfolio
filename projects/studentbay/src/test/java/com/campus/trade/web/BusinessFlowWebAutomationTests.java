package com.campus.trade.web;

import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BusinessFlowWebAutomationTests {

    @Autowired
    private MockMvc web;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    @Test
    void visitorCanRegisterUseSessionLogoutAndLoginAgain() throws Exception {
        String email = "web-" + UUID.randomUUID() + "@campus.edu";
        MockHttpSession session = new MockHttpSession();

        web.perform(post("/api/auth/register").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456","displayName":"自动化同学","role":"USER"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.email").value(email));

        web.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("自动化同学"));

        web.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());
        web.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());

        web.perform(post("/api/auth/login").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));
        web.perform(post("/api/auth/register").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456","displayName":"重复用户","role":"USER"}
                                """.formatted(email)))
                .andExpect(status().isConflict());
        web.perform(post("/api/auth/login").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sellerCanPublishAndBuyerCanFilterCompareAndComment() throws Exception {
        AppUser seller = user("chen@campus.edu");
        AppUser buyer = user("lin@campus.edu");
        MockHttpSession sellerSession = session(seller);
        MockHttpSession buyerSession = session(buyer);

        MvcResult published = web.perform(post("/api/products").session(sellerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Web 自动化测试教材","description":"用于验证完整发布流程的测试商品",
                                 "category":"教材资料","condition":"九成新","price":39.00,"originalPrice":88.00,
                                 "imageUrl":"https://example.com/book.jpg","locationName":"图书馆北门",
                                 "latitude":23.1649870,"longitude":113.4461120}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.sellerId").value(seller.getId()))
                .andReturn();
        long productId = dataId(published);

        web.perform(get("/api/products/mine/list").session(sellerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(productId));
        web.perform(get("/api/products").param("category", "教材资料").param("keyword", "自动化"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(productId));
        web.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(1));
        web.perform(get("/api/products/{id}/comparisons", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statistics.sampleCount").isNumber());

        MvcResult comment = web.perform(post("/api/products/{id}/comments", productId).session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"请问可以在图书馆面交吗？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andReturn();
        long commentId = dataId(comment);
        web.perform(get("/api/products/{id}/comments", productId).session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].canDelete").value(true));
        web.perform(delete("/api/products/{productId}/comments/{commentId}", productId, commentId)
                        .session(buyerSession))
                .andExpect(status().isOk());
    }

    @Test
    void buyerCanUseCartFavoritesReportsAndChat() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        Product product = purchasableProductFor(buyer.getId());
        MockHttpSession buyerSession = session(buyer);

        MvcResult firstCartAdd = web.perform(post("/api/cart/{id}", product.getId()).session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andReturn();
        long cartItemId = dataId(firstCartAdd);
        web.perform(post("/api/cart/{id}", product.getId()).session(buyerSession))
                .andExpect(status().isOk());
        web.perform(get("/api/cart/count").session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
        web.perform(delete("/api/cart/{id}", cartItemId).session(buyerSession))
                .andExpect(status().isOk());
        web.perform(get("/api/cart/count").session(buyerSession))
                .andExpect(jsonPath("$.data").value(0));
        web.perform(post("/api/cart/{id}", product.getId()).session(buyerSession))
                .andExpect(status().isOk());

        web.perform(post("/api/favorites/{id}/toggle", product.getId()).session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
        web.perform(post("/api/favorites/{id}/toggle", product.getId()).session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        web.perform(post("/api/favorites/{id}", product.getId()).session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
        web.perform(get("/api/favorites").session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(product.getId()));

        web.perform(post("/api/reports").session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"reason":"信息可能不准确","detail":"Web 自动化举报流程"}
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        MvcResult started = web.perform(post("/api/chat/product/{id}", product.getId()).session(buyerSession))
                .andExpect(status().isOk())
                .andReturn();
        long conversationId = dataId(started);
        web.perform(post("/api/chat/product/{id}", product.getId()).session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(conversationId));
        web.perform(post("/api/chat/conversations/{id}/messages", conversationId).session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"今晚可以面交吗？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mine").value(true));

        MockHttpSession sellerSession = session(users.findById(product.getSellerId()).orElseThrow());
        web.perform(get("/api/chat/unread-count").session(sellerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
        web.perform(get("/api/chat/conversations/{id}/messages", conversationId).session(sellerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("今晚可以面交吗？"));
    }

    @Test
    void userCanMaintainAddressesProfileCouponsAndRewards() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        MockHttpSession buyerSession = session(buyer);

        MvcResult created = web.perform(post("/api/account/addresses").session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"林同学","phone":"13800138000",
                                 "detail":"广东东软学院南区宿舍 3 栋","defaultAddress":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andReturn();
        long addressId = dataId(created);
        web.perform(get("/api/account/addresses").session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(addressId));
        web.perform(put("/api/account/addresses/{id}", addressId).session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"林同学","phone":"13900139000",
                                 "detail":"广东东软学院南区宿舍 3 栋 201","defaultAddress":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13900139000"));

        web.perform(put("/api/account/profile").session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"林同学（已更新）","email":"lin@campus.edu","university":"广东东软学院"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("林同学（已更新）"));
        web.perform(get("/api/account/coupons").session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        web.perform(get("/api/account/bought").session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        web.perform(multipart("/api/account/avatar").session(buyerSession))
                .andExpect(status().isBadRequest());

        web.perform(get("/api/rewards/summary").session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks").isArray())
                .andExpect(jsonPath("$.data.game.remainingRolls").value(3));
        web.perform(post("/api/rewards/tasks/DAILY_CHECK_IN/claim").session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardCoins").value(3));
        for (int roll = 0; roll < 3; roll++) {
            web.perform(post("/api/rewards/game/play").session(buyerSession))
                    .andExpect(status().isOk());
        }
        web.perform(post("/api/rewards/game/play").session(buyerSession))
                .andExpect(status().isConflict());

        web.perform(delete("/api/account/addresses/{id}", addressId).session(buyerSession))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanInspectGovernanceQueueAndModerateAReport() throws Exception {
        AppUser buyer = user("lin@campus.edu");
        AppUser admin = user("admin@studentbay.local");
        Product product = purchasableProductFor(buyer.getId());

        MvcResult report = web.perform(post("/api/reports").session(session(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"reason":"违规信息","detail":"等待管理员处理"}
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andReturn();
        long reportId = dataId(report);
        MockHttpSession adminSession = session(admin);

        web.perform(get("/api/admin/dashboard").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingReports").value(1))
                .andExpect(jsonPath("$.data.dailyTrend.length()").value(7));
        web.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        web.perform(get("/api/admin/products").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        web.perform(get("/api/admin/reports").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(reportId));
        web.perform(post("/api/admin/reports/{id}/process", reportId).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"已复核并下架\",\"takeDownProduct\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
        assertThat(products.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.OFF_SHELF);
        web.perform(patch("/api/admin/products/{id}/status", product.getId()).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
        web.perform(patch("/api/admin/users/{id}/status", buyer.getId()).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        web.perform(patch("/api/admin/users/{id}/status", buyer.getId()).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        web.perform(put("/api/admin/map-config").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoom\":\"17\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.zoom").value("17"));
        web.perform(get("/api/map/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.zoom").value("17"));
    }

    private AppUser user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }

    private MockHttpSession session(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());
        return session;
    }

    private Product purchasableProductFor(Long buyerId) {
        return products.findAll().stream()
                .filter(product -> product.getStatus() == ProductStatus.AVAILABLE)
                .filter(product -> !product.getSellerId().equals(buyerId))
                .findFirst().orElseThrow();
    }

    private long dataId(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }
}
