package com.campus.trade.controller.product;

import com.campus.trade.repository.product.ProductRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T5.11 平台其他模块缺口补测。
 *
 * <p>覆盖盘点中此前无专门测试的模块：购物车、商品留言、自然语言搜索、
 * 收藏、校园 POI 逆查、积分（签到/游戏）、后台看板（管理员鉴权）、地理编码。
 * 演示账号：lin@campus.edu（买家）/ chen@campus.edu（卖家）/
 * admin@studentbay.local（管理员），密码 123456。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlatformGapIntegrationTests {

    @Autowired
    private MockMvc web;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private ProductRepository products;

    private MockHttpSession login(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        web.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return session;
    }

    /** 用 chen 发布一件在售商品（卖家=陈同学），返回商品 id */
    private long publishByChen(String title) throws Exception {
        MockHttpSession chen = login("chen@campus.edu");
        MvcResult result = web.perform(post("/api/products").session(chen)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"缺口补测商品","category":"数码产品","condition":"九成新",
                                 "price":50,"originalPrice":60,"locationName":"教学楼B栋",
                                 "latitude":23.14,"longitude":113.32,
                                 "addressCountry":"中国","addressProvince":"广东","addressCity":"佛山",
                                 "addressDistrict":"南海区","addressStreet":"大学路"}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    // ---------- 1. 购物车 ----------

    @Test
    void cartShouldAddListCountAndRemove() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long pid = publishByChen("购物车测试商品");

        // 未登录 → 401
        web.perform(post("/api/cart/{id}", pid))
                .andExpect(status().isUnauthorized());

        // 加购 → count=1；重复加购（幂等去重）
        web.perform(post("/api/cart/{id}", pid).session(lin))
                .andExpect(status().isOk());
        web.perform(post("/api/cart/{id}", pid).session(lin))
                .andExpect(status().isOk());
        web.perform(get("/api/cart/count").session(lin))
                .andExpect(jsonPath("$.data").value(1));

        // 列表含该商品
        web.perform(get("/api/cart").session(lin))
                .andExpect(jsonPath("$.data[0].productId").value(pid));

        // 加自己的商品 → 400
        MockHttpSession me = login("lin@campus.edu");
        MvcResult mine = web.perform(post("/api/products").session(me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"自己的商品","description":"x","category":"数码产品","condition":"九成新",
                                 "price":10,"locationName":"A栋","latitude":23.1,"longitude":113.3}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long myId = json.readTree(mine.getResponse().getContentAsString()).path("data").path("id").asLong();
        web.perform(post("/api/cart/{id}", myId).session(me))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("自己")));

        // 删除 → count=0
        MvcResult cartList = web.perform(get("/api/cart").session(lin))
                .andReturn();
        long itemId = json.readTree(cartList.getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        web.perform(delete("/api/cart/{id}", itemId).session(lin))
                .andExpect(status().isOk());
        web.perform(get("/api/cart/count").session(lin))
                .andExpect(jsonPath("$.data").value(0));
    }

    // ---------- 2. 商品留言 ----------

    @Test
    void commentShouldAddListAndEnforceOwnership() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long pid = publishByChen("留言测试商品");

        // 未登录发留言 → 401
        web.perform(post("/api/products/{id}/comments", pid)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"在吗？\"}"))
                .andExpect(status().isUnauthorized());

        // 发留言 → 列表可见（作者=林同学）
        web.perform(post("/api/products/{id}/comments", pid).session(lin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"这本书还在吗？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("这本书还在吗？"));
        web.perform(get("/api/products/{id}/comments", pid).session(lin))
                .andExpect(jsonPath("$.data[0].authorName").value("林同学"));

        // 他人（wang）删除 → 403
        MockHttpSession wang = login("wang@campus.edu");
        MvcResult list = web.perform(get("/api/products/{id}/comments", pid).session(lin))
                .andReturn();
        long commentId = json.readTree(list.getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        web.perform(delete("/api/products/{id}/comments/{commentId}", pid, commentId).session(wang))
                .andExpect(status().isForbidden());

        // 本人删除 → 列表为空
        web.perform(delete("/api/products/{id}/comments/{commentId}", pid, commentId).session(lin))
                .andExpect(status().isOk());
        web.perform(get("/api/products/{id}/comments", pid).session(lin))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ---------- 3. 自然语言搜索 ----------

    @Test
    void naturalSearchShouldReturnInterpretedProducts() throws Exception {
        publishByChen("自然语言搜索教材-高等数学");

        web.perform(post("/api/products/natural-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"我想要一本高数教材\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query").exists())
                .andExpect(jsonPath("$.data.mode").exists())
                .andExpect(jsonPath("$.data.interpretation").exists())
                .andExpect(jsonPath("$.data.products").isArray());
    }

    // ---------- 4. 收藏 ----------

    @Test
    void favoriteShouldToggleAndList() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long pid = publishByChen("收藏测试商品");

        // 未登录 → 401
        web.perform(post("/api/favorites/{id}/toggle", pid))
                .andExpect(status().isUnauthorized());

        // 收藏 → active=true
        web.perform(post("/api/favorites/{id}/toggle", pid).session(lin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.count").value(1));
        web.perform(get("/api/favorites").session(lin))
                .andExpect(jsonPath("$.data[0].id").value(pid));

        // 再 toggle → 取消收藏
        web.perform(post("/api/favorites/{id}/toggle", pid).session(lin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        web.perform(get("/api/favorites").session(lin))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ---------- 5. 校园 POI 逆查 ----------

    @Test
    void poiNearestShouldReturnCampusLocation() throws Exception {
        // 用种子 POI「图书馆东门」(23.1386, 113.0259) 附近坐标，2km 内命中
        web.perform(get("/api/pois/nearest")
                        .param("lat", "23.1386").param("lng", "113.0259"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.poi.name").isNotEmpty());
    }

    // ---------- 6. 积分任务与每日游戏 ----------

    @Test
    void rewardCheckInAndDailyGameShouldWork() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");

        // 概览：钱包余额为「金币」，任务列表
        web.perform(get("/api/rewards/summary").session(lin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coinBalance").isNumber())
                .andExpect(jsonPath("$.data.tasks").isArray());

        // 签到领奖
        web.perform(post("/api/rewards/tasks/DAILY_CHECK_IN/claim").session(lin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardCoins").isNumber())
                .andExpect(jsonPath("$.data.coinBalance").isNumber())
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("已到账")));

        // 同日重复签到 → 409（已领取）
        web.perform(post("/api/rewards/tasks/DAILY_CHECK_IN/claim").session(lin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已经领取")));

        // 每日游戏（骰子）→ 返回点数与剩余次数
        web.perform(post("/api/rewards/game/play").session(lin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dice").isNumber())
                .andExpect(jsonPath("$.data.remainingRolls").isNumber());
    }

    // ---------- 7. 后台看板（管理员鉴权） ----------

    @Test
    void adminDashboardShouldRequireAdminRole() throws Exception {
        // 普通用户 → 403
        MockHttpSession lin = login("lin@campus.edu");
        web.perform(get("/api/admin/dashboard").session(lin))
                .andExpect(status().isForbidden());

        // 管理员 → 200 且含统计字段
        MockHttpSession admin = login("admin@studentbay.local");
        web.perform(get("/api/admin/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").isNumber())
                .andExpect(jsonPath("$.data.totalUsers").isNumber());

        // 管理员用户列表
        web.perform(get("/api/admin/users").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---------- 8. 地理编码（正/逆） ----------

    @Test
    void geocodeShouldResolveAddressAndReverse() throws Exception {
        // 正向：地址 → 坐标（无外网 key 时兜底校园 POI，仍返回 200）
        web.perform(get("/api/geocode").param("address", "大学路"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 逆向：坐标 → 地址
        web.perform(get("/api/geocode/reverse")
                        .param("lat", "23.1400").param("lng", "113.3200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ---------- 9. 举报（提交 + 管理员处理） ----------

    @Test
    void reportShouldSubmitAndAdminProcess() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long pid = publishByChen("举报处理测试商品");

        // 举报自己的商品 → 400
        MockHttpSession me = login("lin@campus.edu");
        MvcResult mine = web.perform(post("/api/products").session(me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"举报自己的商品","description":"x","category":"数码产品","condition":"九成新",
                                 "price":10,"locationName":"A栋","latitude":23.1,"longitude":113.3}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long myId = json.readTree(mine.getResponse().getContentAsString()).path("data").path("id").asLong();
        web.perform(post("/api/reports").session(me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"reason":"违规","detail":"测试"}
                                """.formatted(myId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("自己")));

        // 举报他人商品 → 201（业务码 201）
        web.perform(post("/api/reports").session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"reason":"描述不符","detail":"收到与描述不一致"}
                                """.formatted(pid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // 管理员：举报列表可见 → 处理（RESOLVED + 下架商品）
        MockHttpSession admin = login("admin@studentbay.local");
        MvcResult list = web.perform(get("/api/admin/reports").session(admin))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reports = json.readTree(list.getResponse().getContentAsString()).path("data");
        JsonNode target = null;
        for (JsonNode node : reports) {
            if (node.path("productId").asLong() == pid) {
                target = node;
            }
        }
        assertThat(target).as("管理员应看到该举报").isNotNull();
        long reportId = target.path("id").asLong();
        web.perform(post("/api/admin/reports/{id}/process", reportId).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"RESOLVED","adminNote":"核实属实","takeDownProduct":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        // 处理时选择下架 → 商品变为 OFF_SHELF
        assertThat(products.findById(pid).orElseThrow().getStatus().name()).isEqualTo("OFF_SHELF");
    }

    // ---------- 10. 后台商品管理 ----------

    @Test
    void adminProductManagementShouldListAndChangeStatus() throws Exception {
        long pid = publishByChen("后台管理测试商品");

        // 普通用户 → 403
        MockHttpSession lin = login("lin@campus.edu");
        web.perform(get("/api/admin/products").session(lin))
                .andExpect(status().isForbidden());

        // 管理员：商品列表含该商品 → 强制下架 → 状态 OFF_SHELF
        MockHttpSession admin = login("admin@studentbay.local");
        web.perform(get("/api/admin/products").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(pid)).exists());
        web.perform(patch("/api/admin/products/{id}/status", pid).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFF_SHELF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"));
    }

    // ---------- 11. 聊天会话（列表 / 未读 / 已读回执） ----------

    @Test
    void chatConversationShouldListUnreadAndMarkRead() throws Exception {
        long pid = publishByChen("聊天未读测试商品");

        // 买家发起会话并发消息
        MockHttpSession lin = login("lin@campus.edu");
        MvcResult start = web.perform(post("/api/chat/product/{id}", pid).session(lin))
                .andExpect(status().isOk())
                .andReturn();
        long conversationId = json.readTree(start.getResponse().getContentAsString()).path("data").path("id").asLong();
        web.perform(post("/api/chat/conversations/{id}/messages", conversationId).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"请问还在吗？\"}"))
                .andExpect(status().isOk());

        // 卖家视角：SELLER 箱出现该会话、未读数 ≥1
        MockHttpSession chen = login("chen@campus.edu");
        MvcResult sellerBox = web.perform(get("/api/chat/conversations").session(chen).param("box", "SELLER"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode box = json.readTree(sellerBox.getResponse().getContentAsString()).path("data");
        assertThat(box.get(0).path("productId").asLong()).isEqualTo(pid);
        assertThat(box.get(0).path("unreadCount").asLong()).isGreaterThanOrEqualTo(1L);
        MvcResult unread = web.perform(get("/api/chat/unread-count").session(chen))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json.readTree(unread.getResponse().getContentAsString()).path("data").asLong())
                .isGreaterThanOrEqualTo(1L);

        // 卖家读取消息 → 已读回执（unread=0）
        web.perform(get("/api/chat/conversations/{id}/messages", conversationId).session(chen))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].read").value(true));
        MvcResult unread2 = web.perform(get("/api/chat/unread-count").session(chen))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json.readTree(unread2.getResponse().getContentAsString()).path("data").asLong()).isZero();
    }

    // ---------- 12. 会话生命周期（注册 → 登录 → 登出） ----------

    @Test
    void authSessionShouldInvalidateAfterLogout() throws Exception {
        String email = "gap-" + java.util.UUID.randomUUID() + "@campus.edu";
        MockHttpSession session = new MockHttpSession();

        // 注册
        web.perform(post("/api/auth/register").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456","displayName":"缺口测试用户","role":"USER"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 会话内可访问个人信息
        web.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("缺口测试用户"));

        // 登出后会话失效 → 401
        web.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());
        web.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}
