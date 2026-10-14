package com.campus.trade.controller.product;

import com.campus.trade.entity.chat.ChatMessage;
import com.campus.trade.entity.chat.Conversation;
import com.campus.trade.entity.product.HotKeyword;
import com.campus.trade.entity.product.PriceCutRecord;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductBrowse;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.repository.chat.AutoReplySettingRepository;
import com.campus.trade.repository.chat.ChatMessageRepository;
import com.campus.trade.repository.chat.ConversationRepository;
import com.campus.trade.repository.product.HotKeywordRepository;
import com.campus.trade.repository.product.PriceCutRecordRepository;
import com.campus.trade.repository.product.ProductBrowseRepository;
import com.campus.trade.repository.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T5.10 商品中心全链路集成测试（核心）。
 *
 * <p>覆盖 user2 全部业务闭环：发布校验 / 草稿流转 / 上下架 / 编辑（含旧图移除）/
 * 图片上传白名单 / 砍一刀专属价 / 智能回复（全局+单品+批量+触发一次）/
 * 擦亮置顶 / 最近浏览 / 相似推荐 / 热搜。
 *
 * <p>每个场景独立测试方法；事务回滚保证互不污染；H2 内存库无需 MySQL；
 * 演示账号 lin@campus.edu（卖家）/ chen@campus.edu（买家），密码 123456。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductCenterFullIntegrationTests {

    @Autowired
    private MockMvc web;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private ProductRepository products;
    @Autowired
    private PriceCutRecordRepository priceCutRecords;
    @Autowired
    private ProductBrowseRepository browses;
    @Autowired
    private HotKeywordRepository hotKeywords;
    @Autowired
    private AutoReplySettingRepository replySettings;
    @Autowired
    private ConversationRepository conversations;
    @Autowired
    private ChatMessageRepository messages;

    /** 保证种子分类存在（与 ProductStage4ControllerIntegrationTests 同法） */
    @BeforeEach
    void ensureSeed() {
        products.findAll().forEach(p -> { /* 只读预热 */ });
    }

    // ---------- 工具方法 ----------

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

    /** 发布一个默认合法商品，返回 id。extra 为可选的尾部 JSON 片段（如砍价配置）。 */
    private long publish(MockHttpSession seller, String title, String extra) throws Exception {
        String suffix = extra == null || extra.isBlank() ? "" : "," + extra;
        String body = """
                {"title":"%s","description":"测试商品描述","category":"数码产品","condition":"九成新",
                 "price":100,"originalPrice":120,"locationName":"教学楼A栋",
                 "latitude":23.14,"longitude":113.32,"images":["/uploads/a.png","/uploads/b.png"],
                 "addressCountry":"中国","addressProvince":"广东","addressCity":"佛山",
                 "addressDistrict":"南海区","addressStreet":"大学路"%s}
                """.formatted(title, suffix);
        MvcResult result = web.perform(post("/api/products").session(seller)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long publish(MockHttpSession seller, String title) throws Exception {
        return publish(seller, title, "");
    }

    // ---------- 1. 发布校验 ----------

    @Test
    void publishValidationShouldRejectMissingFieldsAndAcceptValid() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");

        // 未登录 → 401
        web.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // 缺标题 → 400
        web.perform(post("/api/products").session(lin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"缺标题","category":"数码产品","condition":"九成新",
                                 "price":10,"locationName":"A栋","latitude":23.1,"longitude":113.3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("标题")));

        // 缺价格 → 400
        web.perform(post("/api/products").session(lin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"缺价格","description":"x","category":"数码产品","condition":"九成新",
                                 "locationName":"A栋","latitude":23.1,"longitude":113.3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("售价")));

        // 缺经纬度 → 400
        web.perform(post("/api/products").session(lin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"缺地点","description":"x","category":"数码产品","condition":"九成新",
                                 "price":10,"locationName":"A栋"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("地图")));

        // 正常发布 → AVAILABLE、viewCount=0
        long id = publish(lin, "正常发布商品-校验");
        Product saved = products.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
        assertThat(saved.getViewCount()).isZero();
    }

    // ---------- 2. 草稿流转 ----------

    @Test
    void draftShouldSaveListEditPublishAndDelete() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");

        // 草稿：校验只需标题，但 DB 非空列须提供 description/category/condition
        MvcResult draftResult = web.perform(post("/api/products").session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"草稿商品-标题先行","description":"草稿描述","category":"数码产品",
                                 "condition":"九成新","status":"DRAFT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        long draftId = json.readTree(draftResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 草稿栏可见（mine/list?status=DRAFT）
        web.perform(get("/api/products/mine/list").session(lin).param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(draftId)).exists());

        // 草稿 → 编辑并发布（status 由 DRAFT 转 AVAILABLE）
        web.perform(put("/api/products/{id}", draftId).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"草稿转发布","description":"补全描述","category":"数码产品","condition":"九成新",
                                 "price":66,"locationName":"图书馆","latitude":23.15,"longitude":113.33,
                                 "status":"AVAILABLE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        // 再存一个草稿并删除
        MvcResult d2 = web.perform(post("/api/products").session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"待删草稿","description":"草稿描述","category":"数码产品",
                                 "condition":"九成新","status":"DRAFT"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long draftId2 = json.readTree(d2.getResponse().getContentAsString()).path("data").path("id").asLong();
        web.perform(delete("/api/products/{id}", draftId2).session(lin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("草稿已删除"));
        assertThat(products.findById(draftId2)).isEmpty();

        // 非草稿不可删除
        web.perform(delete("/api/products/{id}", draftId).session(lin))
                .andExpect(status().isBadRequest());
    }

    // ---------- 3. 上下架 ----------

    @Test
    void onOffShelfShouldControlPublicVisibility() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long id = publish(lin, "上下架测试商品");

        // 公开列表可见
        web.perform(get("/api/products").param("keyword", "上下架测试商品"))
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 下架 → OFF_SHELF、公开列表不可见
        web.perform(patch("/api/products/{id}/status", id).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFF_SHELF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"));
        web.perform(get("/api/products").param("keyword", "上下架测试商品"))
                .andExpect(jsonPath("$.data.total").value(0));

        // 重新上架 → AVAILABLE、公开列表可见
        web.perform(patch("/api/products/{id}/status", id).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
        web.perform(get("/api/products").param("keyword", "上下架测试商品"))
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 非法状态变更（AVAILABLE → SOLD 不允许直接卖）→ 400
        web.perform(patch("/api/products/{id}/status", id).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SOLD\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 4. 编辑（旧图移除） ----------

    @Test
    void updateShouldReplaceContentAndRemoveOldImages() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long id = publish(lin, "编辑测试商品");

        // 编辑：换标题/价格/图片列表
        web.perform(put("/api/products/{id}", id).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"编辑后新标题","description":"新描述","category":"数码产品","condition":"八成新",
                                 "price":88,"locationName":"食堂","latitude":23.16,"longitude":113.34,
                                 "images":["/uploads/c.png"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("编辑后新标题"))
                .andExpect(jsonPath("$.data.price").value(88))
                .andExpect(jsonPath("$.data.images[0]").value("/uploads/c.png"))
                .andExpect(jsonPath("$.data.images[1]").doesNotExist());

        // 编辑他人商品 → 403
        MockHttpSession chen = login("chen@campus.edu");
        web.perform(put("/api/products/{id}", id).session(chen)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"篡改标题","description":"x","category":"数码产品","condition":"九成新",
                                 "price":1,"locationName":"A栋","latitude":23.1,"longitude":113.3}
                                """))
                .andExpect(status().isForbidden());
    }

    // ---------- 5. 图片上传（白名单） ----------

    @Test
    void imageUploadShouldEnforceLoginTypeAndSize() throws Exception {
        // 未登录 → 401
        web.perform(multipart("/api/uploads").file(new MockMultipartFile(
                        "file", "a.png", "image/png", new byte[]{1, 2, 3})))
                .andExpect(status().isUnauthorized());

        MockHttpSession lin = login("lin@campus.edu");
        // 合法 png → 200 + /uploads/ 开头
        web.perform(multipart("/api/uploads").file(new MockMultipartFile(
                        "file", "photo.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}))
                        .session(lin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/uploads/")));

        // 非法类型 txt → 400
        web.perform(multipart("/api/uploads").file(new MockMultipartFile(
                        "file", "evil.txt", "text/plain", "hello".getBytes()))
                        .session(lin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("格式")));

        // 超 5MB → 400
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        web.perform(multipart("/api/uploads").file(new MockMultipartFile(
                        "file", "big.png", "image/png", big))
                        .session(lin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("5MB")));
    }

    // ---------- 6. 砍一刀（专属价） ----------

    @Test
    void bargainShouldCreateBuyerExclusivePriceOnlyOnce() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long id = publish(lin, "砍价测试商品",
                "\"cutEnabled\":true,\"cutMin\":1,\"cutMax\":3,\"cutLimit\":3");

        MockHttpSession chen = login("chen@campus.edu");

        // 买家砍价成功：priceAfter < 100，优惠 1~3 元
        MvcResult cut = web.perform(post("/api/products/{id}/bargain", id).session(chen))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceAfter").value(org.hamcrest.Matchers.lessThan(100.0)))
                .andExpect(jsonPath("$.data.priceBefore").value(100))
                .andReturn();
        JsonNode cutData = json.readTree(cut.getResponse().getContentAsString()).path("data");
        BigDecimal amount = new BigDecimal(cutData.path("amount").asText());
        assertThat(amount).isBetween(new BigDecimal("1.00"), new BigDecimal("3.00"));

        // 重复砍价 → 409
        web.perform(post("/api/products/{id}/bargain", id).session(chen))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("砍过")));

        // 挂牌原价不变（公开列表仍是 100）
        web.perform(get("/api/products").param("keyword", "砍价测试商品"))
                .andExpect(jsonPath("$.data.items[0].price").value(100));

        // 买家视角：cutByMe=true 且能看到专属价
        web.perform(get("/api/products/{id}", id).session(chen))
                .andExpect(jsonPath("$.data.cutByMe").value(true))
                .andExpect(jsonPath("$.data.cutPrice").exists());

        // 卖家视角：cutByMe=false、无专属价
        web.perform(get("/api/products/{id}", id).session(lin))
                .andExpect(jsonPath("$.data.cutByMe").value(false))
                .andExpect(jsonPath("$.data.cutPrice").doesNotExist());

        // 未开启砍价的商品被砍 → 400
        long noCut = publish(lin, "未开启砍价商品");
        web.perform(post("/api/products/{id}/bargain", noCut).session(chen))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("未开启")));

        // 卖家不能砍自己的商品 → 403
        web.perform(post("/api/products/{id}/bargain", id).session(lin))
                .andExpect(status().isForbidden());
    }

    // ---------- 7. 智能回复（全局/单品/批量/触发一次） ----------

    @Test
    void autoReplyShouldCoverGlobalProductApplyAllAndTriggerOnce() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long id = publish(lin, "自动回复测试商品");

        // 全局默认：关闭、0 个已应用
        web.perform(get("/api/auto-reply/global").session(lin))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.appliedCount").value(0));

        // 开启但内容为空 → 400
        web.perform(put("/api/auto-reply/global").session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"content\":\"\"}"))
                .andExpect(status().isBadRequest());

        // 保存全局：支持 {商品标题} / {价格} / {昵称} 变量替换
        web.perform(put("/api/auto-reply/global").session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"content\":\"你好，这件{商品标题}还在的，可以聊～\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        // 一键应用（不覆盖）：apply-all → 该商品被应用
        web.perform(post("/api/auto-reply/apply-all").session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overwrite\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.skipped").isNumber());
        assertThat(replySettings.findByUserId(1L)).isPresent();

        // ① 买家发起会话 → 命中全局回复：{title} 变量被替换、sender=卖家、autoReply=true
        MockHttpSession chen = login("chen@campus.edu");
        MvcResult start = web.perform(post("/api/chat/product/{id}", id).session(chen))
                .andExpect(status().isOk())
                .andReturn();
        long conversationId = json.readTree(start.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        MvcResult msgs = web.perform(get("/api/chat/conversations/{id}/messages", conversationId).session(chen))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = json.readTree(msgs.getResponse().getContentAsString()).path("data");
        JsonNode auto = null;
        for (JsonNode node : list) {
            if (node.path("autoReply").asBoolean()) {
                auto = node;
            }
        }
        assertThat(auto).as("应存在一条自动回复").isNotNull();
        assertThat(auto.path("senderName").asText()).isEqualTo("林同学");
        assertThat(auto.path("content").asText()).contains("自动回复测试商品").contains("可以聊");

        // 重复触发（同一买家再次 start/发消息）→ 不再新增自动回复（replyOnce 默认 true）
        web.perform(post("/api/chat/product/{id}", id).session(chen))
                .andExpect(status().isOk());
        MvcResult msgs2 = web.perform(get("/api/chat/conversations/{id}/messages", conversationId).session(chen))
                .andReturn();
        long autoCount = json.readTree(msgs2.getResponse().getContentAsString()).path("data")
                .findValues("autoReply").stream().filter(JsonNode::asBoolean).count();
        assertThat(autoCount).isEqualTo(1);

        // ② 单品覆盖：专属文本生效，命中单品而非全局
        web.perform(put("/api/products/{id}/auto-reply", id).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"text\":\"本件有专属优惠，可直接小刀\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").value("本件有专属优惠，可直接小刀"));
        web.perform(get("/api/products/{id}/auto-reply", id).session(lin))
                .andExpect(jsonPath("$.data.enabled").value(true));
        MockHttpSession wang = login("wang@campus.edu");
        MvcResult start2 = web.perform(post("/api/chat/product/{id}", id).session(wang))
                .andExpect(status().isOk())
                .andReturn();
        long conversationId2 = json.readTree(start2.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        MvcResult msgs3 = web.perform(get("/api/chat/conversations/{id}/messages", conversationId2).session(wang))
                .andReturn();
        JsonNode list3 = json.readTree(msgs3.getResponse().getContentAsString()).path("data");
        JsonNode auto3 = null;
        for (JsonNode node : list3) {
            if (node.path("autoReply").asBoolean()) {
                auto3 = node;
            }
        }
        assertThat(auto3).as("单品开启后新买家应收到专属回复").isNotNull();
        assertThat(auto3.path("content").asText()).isEqualTo("本件有专属优惠，可直接小刀");

        // ③ 关闭全局后，未设置单品的新商品不再触发
        web.perform(put("/api/auto-reply/global").session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"content\":\"\"}"))
                .andExpect(status().isOk());
        long second = publish(lin, "关闭全局后商品");
        web.perform(post("/api/chat/product/{id}", second).session(wang))
                .andExpect(status().isOk());
        List<Conversation> convs = conversations.findByProductIdAndBuyerId(second, 3L).stream().toList();
        assertThat(convs).hasSize(1);
        List<ChatMessage> msgsFinal = messages.findByConversationIdOrderByCreatedAtAsc(convs.get(0).getId());
        assertThat(msgsFinal.stream().anyMatch(ChatMessage::getAutoReply)).isFalse();
    }

    // ---------- 8. 擦亮 / 置顶 ----------

    @Test
    void sharpenAndTopShouldEnforceOwnershipLimitAndPriority() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long id = publish(lin, "擦亮置顶测试商品");

        // 非本人擦亮 → 403
        MockHttpSession chen = login("chen@campus.edu");
        web.perform(post("/api/products/{id}/sharpen", id).session(chen))
                .andExpect(status().isForbidden());

        // 擦亮成功
        web.perform(post("/api/products/{id}/sharpen", id).session(lin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sharpenedAt").exists());

        // 同日再次擦亮 → 409
        web.perform(post("/api/products/{id}/sharpen", id).session(lin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("擦亮")));

        // 置顶时长非法 → 400（0 小时 / 超过 720）
        web.perform(post("/api/products/{id}/top", id).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\":0}"))
                .andExpect(status().isBadRequest());
        web.perform(post("/api/products/{id}/top", id).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\":1000}"))
                .andExpect(status().isBadRequest());

        // 置顶 24h → topExpireAt 存在
        web.perform(post("/api/products/{id}/top", id).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\":24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topExpireAt").exists());

        // 置顶排序优先：搜索该关键词时该商品位于最前
        MvcResult list = web.perform(get("/api/products").param("keyword", "擦亮置顶测试商品")
                        .param("size", "20"))
                .andReturn();
        JsonNode items = json.readTree(list.getResponse().getContentAsString()).path("data").path("items");
        assertThat(items.size()).isGreaterThan(0);
        assertThat(items.get(0).path("id").asLong()).isEqualTo(id);
    }

    // ---------- 9. 最近浏览 ----------

    @Test
    void recentBrowseShouldDeduplicateOrderReverseAndClear() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long a = publish(lin, "浏览商品A");
        long b = publish(lin, "浏览商品B");

        MockHttpSession chen = login("chen@campus.edu");

        // 未登录 → 401
        web.perform(get("/api/products/browse/recent"))
                .andExpect(status().isUnauthorized());

        // 浏览 A → 记录 1 条；再浏览 A（去重）→ 仍 1 条；再浏览 B → 2 条且 B 在前（倒序）
        web.perform(get("/api/products/{id}", a).session(chen)).andExpect(status().isOk());
        web.perform(get("/api/products/{id}", a).session(chen)).andExpect(status().isOk());
        web.perform(get("/api/products/{id}", b).session(chen)).andExpect(status().isOk());
        MvcResult recent = web.perform(get("/api/products/browse/recent").session(chen))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = json.readTree(recent.getResponse().getContentAsString()).path("data");
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).path("id").asLong()).isEqualTo(b);
        assertThat(list.get(1).path("id").asLong()).isEqualTo(a);

        // 清空
        web.perform(delete("/api/products/browse/clear").session(chen))
                .andExpect(status().isOk());
        web.perform(get("/api/products/browse/recent").session(chen))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ---------- 10. 相似推荐 ----------

    @Test
    void recommendShouldExcludeSelfAndOffShelfAndLimitToEight() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");
        long a = publish(lin, "推荐测试-同款键盘");
        long b = publish(lin, "推荐测试-同款键盘2");
        publish(lin, "推荐测试-同款键盘3");
        // 下架其中一个
        web.perform(patch("/api/products/{id}/status", b).session(lin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFF_SHELF\"}"))
                .andExpect(status().isOk());

        MvcResult rec = web.perform(get("/api/products/recommend").param("productId", String.valueOf(a)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = json.readTree(rec.getResponse().getContentAsString()).path("data");
        assertThat(items.size()).isLessThanOrEqualTo(8);
        List<Long> ids = items.findValues("id").stream().map(JsonNode::asLong).toList();
        assertThat(ids).doesNotContain(a);       // 排除自身
        assertThat(ids).doesNotContain(b);       // 排除已下架
        assertThat(ids).isNotEmpty();            // 仍有同类在卖商品
    }

    // ---------- 11. 热搜 ----------

    @Test
    void hotKeywordShouldCountSearchAndReturnTopTen() throws Exception {
        MockHttpSession lin = login("lin@campus.edu");

        // 搜索三次"蓝牙耳机"，触发热搜计数
        for (int i = 0; i < 3; i++) {
            web.perform(get("/api/products").param("keyword", "蓝牙耳机"))
                    .andExpect(status().isOk());
        }
        MvcResult hot = web.perform(get("/api/products/hot-keywords").session(lin))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = json.readTree(hot.getResponse().getContentAsString()).path("data");
        assertThat(list.size()).isLessThanOrEqualTo(10);

        HotKeyword keyword = hotKeywords.findByKeyword("蓝牙耳机").orElse(null);
        assertThat(keyword).isNotNull();
        assertThat(keyword.getCount()).isGreaterThanOrEqualTo(3L);

        // 计数倒序：第一位的 count >= 第二位的 count
        assertThat(list.get(0).path("count").asLong())
                .isGreaterThanOrEqualTo(list.size() > 1 ? list.get(1).path("count").asLong() : 0);
    }
}
