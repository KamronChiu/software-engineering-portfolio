package com.campus.trade.controller.chat;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.chat.ChatMessage;
import com.campus.trade.entity.chat.Conversation;
import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.order.PaymentStatus;
import com.campus.trade.entity.order.TradeOrder;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.chat.ChatMessageRepository;
import com.campus.trade.repository.chat.ConversationRepository;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.chat.AutoReplyService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final AutoReplyService autoReplyService;

    public ChatController(ConversationRepository conversations, ChatMessageRepository messages,
                          ProductRepository products, OrderRepository orders,
                          UserRepository users, CurrentUserService currentUser, AutoReplyService autoReplyService) {
        this.conversations = conversations;
        this.messages = messages;
        this.products = products;
        this.orders = orders;
        this.users = users;
        this.currentUser = currentUser;
        this.autoReplyService = autoReplyService;
    }

    @PostMapping("/product/{productId}")
    public ApiResponse<Map<String, Object>> start(@PathVariable Long productId, HttpSession session) {
        Long buyerId = currentUser.require(session).getId();
        Product product = requireProduct(productId);
        if (product.getSellerId().equals(buyerId)) throw new BusinessException(HttpStatus.BAD_REQUEST, "这是你发布的商品");
        Conversation conversation = conversations.findByProductIdAndBuyerId(productId, buyerId).orElseGet(() -> {
            Conversation created = new Conversation();
            created.setProductId(productId);
            created.setBuyerId(buyerId);
            created.setSellerId(product.getSellerId());
            return conversations.save(created);
        });
        autoReplyService.maybeAutoReply(conversation, buyerId);
        return ApiResponse.success(conversationView(conversation, buyerId));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<Map<String, Object>>> conversationList(@RequestParam(defaultValue = "BUYER") String box,
                                                                    HttpSession session) {
        Long userId = currentUser.require(session).getId();
        List<Conversation> list = "SELLER".equalsIgnoreCase(box)
                ? conversations.findBySellerIdOrderByUpdatedAtDesc(userId)
                : conversations.findByBuyerIdOrderByUpdatedAtDesc(userId);
        return ApiResponse.success(list.stream().map(item -> conversationView(item, userId)).toList());
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<Map<String, Object>>> messageList(@PathVariable Long id, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        Conversation conversation = requireParticipant(id, userId);
        List<ChatMessage> list = messages.findByConversationIdOrderByCreatedAtAsc(id);
        list.stream().filter(message -> !message.getSenderId().equals(userId) && message.getReadAt() == null).forEach(message -> {
            message.setReadAt(LocalDateTime.now());
            messages.save(message);
        });
        return ApiResponse.success(list.stream().map(message -> messageView(message, userId)).toList());
    }

    @PostMapping("/conversations/{id}/messages")
    public ApiResponse<Map<String, Object>> send(@PathVariable Long id, @Valid @RequestBody SendRequest request,
                                                 HttpSession session) {
        Long userId = currentUser.require(session).getId();
        Conversation conversation = requireParticipant(id, userId);
        ChatMessage message = new ChatMessage();
        message.setConversationId(id);
        message.setSenderId(userId);
        message.setContent(request.content().trim());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversations.save(conversation);
        ChatMessage saved = messages.save(message);
        autoReplyService.maybeAutoReply(conversation, userId);
        return ApiResponse.success(messageView(saved, userId));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(HttpSession session) {
        Long userId = currentUser.require(session).getId();
        List<Long> ids = conversations.findAll().stream()
                .filter(item -> item.getBuyerId().equals(userId) || item.getSellerId().equals(userId))
                .map(Conversation::getId).toList();
        return ApiResponse.success(ids.isEmpty() ? 0L : messages.countByConversationIdInAndSenderIdNotAndReadAtIsNull(ids, userId));
    }

    private Conversation requireParticipant(Long id, Long userId) {
        Conversation conversation = conversations.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "咨询会话不存在"));
        if (!conversation.getBuyerId().equals(userId) && !conversation.getSellerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该会话");
        }
        return conversation;
    }

    private Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "商品不存在"));
    }

    private Map<String, Object> conversationView(Conversation conversation, Long viewerId) {
        Product product = requireProduct(conversation.getProductId());
        Long otherId = conversation.getBuyerId().equals(viewerId) ? conversation.getSellerId() : conversation.getBuyerId();
        String otherName = users.findById(otherId).map(AppUser::getDisplayName).orElse("校园用户");
        List<ChatMessage> list = messages.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        ChatMessage last = list.isEmpty() ? null : list.get(list.size() - 1);
        long unread = list.stream().filter(message -> !message.getSenderId().equals(viewerId) && message.getReadAt() == null).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", conversation.getId());
        result.put("productId", product.getId());
        result.put("productTitle", product.getTitle());
        result.put("productImage", product.getImageUrl());
        result.put("productPrice", product.getPrice());
        result.put("productOriginalPrice", product.getOriginalPrice());
        result.put("productStatus", product.getStatus());
        result.put("productLocation", product.getLocationName());
        result.put("otherName", otherName);
        boolean sellerView = conversation.getSellerId().equals(viewerId);
        result.put("sellerView", sellerView);
        // T5.4 买家视角：卖家开启自动回复（单品或全局默认）时，前端显示"卖家开启了自动回复"提示条
        boolean autoReplyOn = Boolean.TRUE.equals(product.getAutoReplyEnabled())
                || autoReplyService.isGlobalEnabled(product.getSellerId());
        result.put("autoReplyAvailable", !sellerView && autoReplyOn);
        result.put("lastMessage", last == null ? "开始咨询这件商品" : last.getContent());
        result.put("updatedAt", conversation.getUpdatedAt());
        result.put("unreadCount", unread);
        TradeOrder order = orders.findConversationOrders(conversation.getBuyerId(), conversation.getSellerId(),
                conversation.getProductId()).stream().findFirst().orElse(null);
        result.put("order", order == null ? null : orderView(order, viewerId));
        return result;
    }

    private Map<String, Object> orderView(TradeOrder order, Long viewerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("status", order.getStatus());
        result.put("paymentStatus", order.getPaymentStatus());
        result.put("productAmount", order.getProductAmount());
        result.put("shippingCost", order.getShippingCost());
        result.put("totalAmount", order.getTotalAmount());
        result.put("createdAt", order.getCreatedAt());
        result.put("priceAdjustedAt", order.getPriceAdjustedAt());
        result.put("receivedAt", order.getReceivedAt());
        boolean unpaid = order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getPaymentStatus() == PaymentStatus.UNPAID;
        result.put("canCancel", unpaid && order.getBuyerId().equals(viewerId));
        result.put("canAdjustPrice", unpaid && order.getSellerId().equals(viewerId));
        LocalDateTime refundDeadline = order.getReceivedAt() == null ? null : order.getReceivedAt().plusDays(7);
        result.put("refundDeadline", refundDeadline);
        result.put("canRequestRefund", order.getBuyerId().equals(viewerId)
                && order.getPaymentStatus() == PaymentStatus.PAID
                && (order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.COURIER_BOOKED
                || order.getStatus() == OrderStatus.SHIPPED
                || order.getStatus() == OrderStatus.COMPLETED)
                && (refundDeadline == null || !LocalDateTime.now().isAfter(refundDeadline)));
        return result;
    }

    private Map<String, Object> messageView(ChatMessage message, Long viewerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", message.getId());
        result.put("content", message.getContent());
        result.put("mine", message.getSenderId().equals(viewerId));
        result.put("senderName", users.findById(message.getSenderId()).map(AppUser::getDisplayName).orElse("校园用户"));
        result.put("createdAt", message.getCreatedAt());
        result.put("read", message.getReadAt() != null);
        result.put("autoReply", Boolean.TRUE.equals(message.getAutoReply()));
        return result;
    }

    public record SendRequest(@NotBlank @Size(max = 1000) String content) {}
}
