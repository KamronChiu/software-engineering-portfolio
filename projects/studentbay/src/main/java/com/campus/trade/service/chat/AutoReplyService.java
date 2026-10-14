package com.campus.trade.service.chat;

import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.chat.AutoReplySetting;
import com.campus.trade.entity.chat.ChatMessage;
import com.campus.trade.entity.chat.Conversation;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.chat.AutoReplySettingRepository;
import com.campus.trade.repository.chat.ChatMessageRepository;
import com.campus.trade.repository.chat.ConversationRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AutoReplyService {
    private final AutoReplySettingRepository settings;
    private final ChatMessageRepository messages;
    private final ConversationRepository conversations;
    private final ProductRepository products;
    private final UserRepository users;

    public AutoReplyService(AutoReplySettingRepository settings, ChatMessageRepository messages,
                            ConversationRepository conversations, ProductRepository products,
                            UserRepository users) {
        this.settings = settings;
        this.messages = messages;
        this.conversations = conversations;
        this.products = products;
        this.users = users;
    }

    /**
     * 买家在会话里发消息/发起会话时调用：命中卖家回复配置（单品 &gt; 全局 &gt; 不回复），
     * 且该买家此前未收到过该商品的自动回复，则插入一条"卖家（自动）"消息。
     * 返回插入的自动回复消息；未命中或已回复过时返回 null。
     */
    @Transactional
    public ChatMessage maybeAutoReply(Conversation conversation, Long senderId) {
        if (conversation == null || conversation.getSellerId().equals(senderId)) return null;
        Product product = products.findById(conversation.getProductId()).orElse(null);
        if (product == null) return null;
        // 仅在售/已拍下商品触发，已下架/已售/草稿不打扰
        if (product.getStatus() != ProductStatus.AVAILABLE && product.getStatus() != ProductStatus.RESERVED) {
            return null;
        }
        String text = replyText(product, conversation.getSellerId());
        if (text == null) return null;
        List<ChatMessage> history = messages.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        boolean already = history.stream().anyMatch(item -> Boolean.TRUE.equals(item.getAutoReply())
                        && conversation.getSellerId().equals(item.getSenderId()));
        if (already) {
            // T5.4 replyOnce=false 时：买家追问（最后一条是买家发的普通消息）允许再自动回一条；
            // 卖家刚回过则不重复触发，天然防刷
            if (replyOnceFor(conversation.getSellerId())) return null;
            if (history.isEmpty() || !history.get(history.size() - 1).getSenderId().equals(senderId)) return null;
        }

        ChatMessage reply = new ChatMessage();
        reply.setConversationId(conversation.getId());
        reply.setSenderId(conversation.getSellerId());
        reply.setContent(render(text, product, conversation.getBuyerId()));
        reply.setAutoReply(true);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversations.save(conversation);
        return messages.save(reply);
    }

    /** 单品配置优先，其次全局默认，否则不回复 */
    private String replyText(Product product, Long sellerId) {
        if (Boolean.TRUE.equals(product.getAutoReplyEnabled()) && !blank(product.getAutoReplyText())) {
            return product.getAutoReplyText();
        }
        return settings.findByUserId(sellerId)
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()) && !blank(item.getContent()))
                .map(AutoReplySetting::getContent)
                .orElse(null);
    }

    /** 模板变量替换：{昵称} {商品标题} {商品价格} {价格} */
    private String render(String text, Product product, Long buyerId) {
        String nick = users.findById(buyerId).map(AppUser::getDisplayName).orElse("同学");
        String price = money(product.getPrice());
        return text.replace("{昵称}", nick)
                .replace("{商品标题}", product.getTitle())
                .replace("{商品价格}", price)
                .replace("{价格}", price);
    }

    /** T5.4 一次性规则：默认 true；配置为 false 时允许买家追问后再自动回一条 */
    private boolean replyOnceFor(Long sellerId) {
        return settings.findByUserId(sellerId)
                .map(item -> item.getReplyOnce() == null || Boolean.TRUE.equals(item.getReplyOnce()))
                .orElse(true);
    }

    /** 卖家是否开启全局默认回复（供聊天会话"卖家开启了自动回复"提示条判断） */
    public boolean isGlobalEnabled(Long sellerId) {
        return settings.findByUserId(sellerId)
                .map(item -> Boolean.TRUE.equals(item.getEnabled()) && !blank(item.getContent()))
                .orElse(false);
    }

    private String money(BigDecimal value) {
        if (value == null) return "";
        String plain = value.stripTrailingZeros().toPlainString();
        return plain.contains(".") ? "¥" + plain : "¥" + plain + ".00";
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * T5.2 一键应用到全部在卖商品：把全局默认回复内容批量写入每个 AVAILABLE 商品。
     * overwrite=false 时跳过已单独设置过（单品开启且有内容）的商品；true 时全部覆盖。
     * 事务内批量写入，异常整体回滚；返回 {applied, skipped, overwritten}。
     */
    @Transactional
    public Map<String, Integer> applyAll(Long userId, boolean overwrite) {
        AutoReplySetting setting = settings.findByUserId(userId)
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()) && !blank(item.getContent()))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "请先开启所有宝贝默认回复并填写内容"));
        String global = setting.getContent();
        int applied = 0;
        int skipped = 0;
        int overwritten = 0;
        for (Product product : products.findBySellerIdOrderByCreatedAtDesc(userId)) {
            if (product.getStatus() != ProductStatus.AVAILABLE) continue;
            boolean individuallySet = Boolean.TRUE.equals(product.getAutoReplyEnabled()) && !blank(product.getAutoReplyText());
            if (individuallySet && !overwrite) {
                skipped++;
                continue;
            }
            if (individuallySet) {
                overwritten++;
            } else {
                applied++;
            }
            product.setAutoReplyEnabled(true);
            product.setAutoReplyText(global);
            products.save(product);
        }
        Map<String, Integer> result = new HashMap<>();
        result.put("applied", applied);
        result.put("skipped", skipped);
        result.put("overwritten", overwritten);
        return result;
    }

    /** 在卖商品中已开启单品自动回复的数量（面板"已应用到 N 个在卖商品"） */
    public long countAppliedOnSale(Long userId) {
        return products.findBySellerIdOrderByCreatedAtDesc(userId).stream()
                .filter(product -> product.getStatus() == ProductStatus.AVAILABLE)
                .filter(product -> Boolean.TRUE.equals(product.getAutoReplyEnabled()))
                .count();
    }
}
