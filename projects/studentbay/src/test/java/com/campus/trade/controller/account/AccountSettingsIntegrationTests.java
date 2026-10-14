package com.campus.trade.controller.account;

import com.campus.trade.dto.order.CheckoutRequest;
import com.campus.trade.entity.cart.CartItem;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.entity.user.UserRole;
import com.campus.trade.repository.cart.CartRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.order.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖 [Day5] 提交（1719232）我的空间 / 设置中心中尚未被专项测试的部分：
 * 头像上传（格式白名单、持久化、替换旧文件、/me 联动）、改资料换邮箱后的登录身份迁移、
 * 我的买入记录 GET /api/account/bought（按商品聚合、携带订单状态与单号）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountSettingsIntegrationTests {

    @Autowired
    private MockMvc web;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderService orderService;

    private static String unique() {
        return "set-" + UUID.randomUUID().toString().substring(0, 8);
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

    private MockHttpSession sessionOf(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());
        return session;
    }

    private long publish(AppUser seller) throws Exception {
        MvcResult result = web.perform(post("/api/products").session(sessionOf(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"设置测试商品 %s","description":"设置中心链路验证","category":"教材资料",
                                 "condition":"九成新","price":39.00,"originalPrice":88.00,
                                 "imageUrl":"https://example.com/set.jpg","locationName":"图书馆北门",
                                 "latitude":23.1649870,"longitude":113.4461120}
                                """.formatted(unique())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    private long checkout(AppUser buyer, long productId) {
        CartItem item = new CartItem();
        item.setUserId(buyer.getId());
        item.setProductId(productId);
        item.setQuantity(1);
        Long cartItemId = cartRepository.save(item).getId();
        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), null, "PICKUP", null,
                "WECHAT_PAY", false, LocalDate.now(), LocalTime.of(17, 30), LocalTime.of(18, 0),
                "设置测试面交点");
        return orderService.checkout(buyer.getId(), request).get(0).id();
    }

    /** 删除测试落盘的头像文件（uploads/ 不参与 git 回滚）。 */
    private void deleteAvatarFileQuietly(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith("/uploads/")) return;
        try {
            Files.deleteIfExists(Path.of("uploads").resolve(avatarUrl.substring("/uploads/".length())));
        } catch (Exception ignored) {
        }
    }

    @Test
    void avatarUploadShouldRejectEmptyOrUnsupportedFormat() throws Exception {
        AppUser user = newUser("头像校验");

        // 空文件
        web.perform(multipart("/api/account/avatar").session(sessionOf(user))
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest());

        // 非图片扩展名
        web.perform(multipart("/api/account/avatar").session(sessionOf(user))
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain",
                                new byte[]{1, 2, 3, 4})))
                .andExpect(status().isBadRequest());

        // 未登录
        web.perform(multipart("/api/account/avatar")
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void avatarUploadShouldPersistUrlReplaceOldFileAndReflectInMe() throws Exception {
        AppUser user = newUser("头像替换");
        MockHttpSession session = sessionOf(user);
        String firstUrl = null;
        String secondUrl = null;
        try {
            firstUrl = uploadAvatar(session, "first.png");
            assertThat(firstUrl).startsWith("/uploads/avatar-");

            // 新头像立即反映到 /me
            web.perform(get("/api/auth/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.avatarUrl").value(firstUrl));

            // 再次上传：URL 更新，旧文件被删除
            secondUrl = uploadAvatar(session, "second.png");
            assertThat(secondUrl).isNotEqualTo(firstUrl);
            web.perform(get("/api/auth/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.avatarUrl").value(secondUrl));
            assertThat(Files.exists(Path.of("uploads")
                    .resolve(firstUrl.substring("/uploads/".length())))).isFalse();
        } finally {
            deleteAvatarFileQuietly(firstUrl == null ? null : firstUrl);
            deleteAvatarFileQuietly(secondUrl == null ? null : secondUrl);
        }
    }

    private String uploadAvatar(MockHttpSession session, String fileName) throws Exception {
        MvcResult result = web.perform(multipart("/api/account/avatar").session(session)
                        .file(new MockMultipartFile("file", fileName, "image/png", new byte[]{1, 2, 3, 4})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString())
                .path("data").path("avatarUrl").asText();
    }

    @Test
    void profileEmailChangeShouldMigrateLoginIdentity() throws Exception {
        String originalEmail = unique() + "@campus.edu";
        MockHttpSession session = new MockHttpSession();
        web.perform(post("/api/auth/register").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456","displayName":"迁移前","role":"USER"}
                                """.formatted(originalEmail)))
                .andExpect(status().isOk());

        String newEmail = unique() + "@campus.edu";
        web.perform(put("/api/account/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"迁移后","email":"%s","university":"广东理工学院"}
                                """.formatted(newEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(newEmail))
                .andExpect(jsonPath("$.data.displayName").value("迁移后"));

        // 旧邮箱失去登录身份，新邮箱可用原密码登录
        web.perform(post("/api/auth/login").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456"}
                                """.formatted(originalEmail)))
                .andExpect(status().isUnauthorized());
        web.perform(post("/api/auth/login").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456"}
                                """.formatted(newEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("迁移后"));
    }

    @Test
    void boughtListShouldGroupPurchasedProductsWithCurrentOrderState() throws Exception {
        AppUser seller = userByEmail("chen@campus.edu");
        AppUser buyer = userByEmail("lin@campus.edu");

        // 商品一：下单未付款；商品二：完成交易
        long pendingProductId = publish(seller);
        long completedProductId = publish(seller);
        checkout(buyer, pendingProductId);
        long completedOrderId = checkout(buyer, completedProductId);
        orderService.pay(completedOrderId, buyer.getId(), "WECHAT_PAY", "bought-key-" + completedOrderId);
        web.perform(patch("/api/order/{id}/ship", completedOrderId)).andExpect(status().isOk());
        web.perform(patch("/api/order/{id}/receive", completedOrderId)).andExpect(status().isOk());

        // 未登录不可见
        web.perform(get("/api/account/bought"))
                .andExpect(status().isUnauthorized());

        MvcResult result = web.perform(get("/api/account/bought").session(sessionOf(buyer)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode bought = json.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(bought.isArray()).isTrue();

        JsonNode pending = findEntry(bought, pendingProductId);
        assertThat(pending).as("待付款订单应出现在我的买入中").isNotNull();
        assertThat(pending.path("quantity").asInt()).isEqualTo(1);
        assertThat(pending.path("orderStatus").asText()).isEqualTo("PENDING_PAYMENT");

        JsonNode completed = findEntry(bought, completedProductId);
        assertThat(completed).as("已完成订单应出现在我的买入中").isNotNull();
        assertThat(completed.path("orderStatus").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("quantity").asInt()).isEqualTo(1);
        assertThat(completed.path("orderNo").asText()).isNotBlank();
        assertThat(completed.path("boughtAt").asText()).isNotBlank();
        assertThat(completed.path("title").asText()).startsWith("设置测试商品");
        assertThat(completed.path("price").isNumber()).isTrue();
    }

    private JsonNode findEntry(JsonNode array, long productId) {
        for (JsonNode entry : array) {
            if (entry.path("id").asLong() == productId) {
                return entry;
            }
        }
        return null;
    }

    private AppUser userByEmail(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }
}
