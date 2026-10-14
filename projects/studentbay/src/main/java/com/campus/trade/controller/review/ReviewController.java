package com.campus.trade.controller.review;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.chat.ChatMessage;
import com.campus.trade.entity.chat.Conversation;
import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.order.OrderItem;
import com.campus.trade.entity.order.TradeOrder;
import com.campus.trade.entity.review.ReviewType;
import com.campus.trade.entity.review.TradeReview;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.chat.ChatMessageRepository;
import com.campus.trade.repository.chat.ConversationRepository;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.review.TradeReviewRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final TradeReviewRepository reviews;
    private final OrderRepository orders;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final CurrentUserService currentUser;

    public ReviewController(TradeReviewRepository reviews, OrderRepository orders,
                            ConversationRepository conversations, ChatMessageRepository messages,
                            UserRepository users, CurrentUserService currentUser) {
        this.reviews = reviews;
        this.orders = orders;
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateReviewRequest request, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        TradeOrder order = orders.findById(request.orderId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单未完成，暂不能评价");
        }
        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权评价该订单");
        }
        if (reviews.existsByOrderIdAndReviewerId(order.getId(), userId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "你已评价过该订单");
        }
        Long productId = order.getItems().stream().findFirst()
                .map(OrderItem::getProductId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "订单无商品信息"));

        TradeReview review = new TradeReview();
        review.setOrderId(order.getId());
        review.setProductId(productId);
        review.setReviewerId(userId);
        review.setRevieweeId(isBuyer ? order.getSellerId() : order.getBuyerId());
        review.setReviewType(isBuyer ? ReviewType.BUYER_REVIEWS_SELLER : ReviewType.SELLER_REVIEWS_BUYER);
        review.setRating(request.rating());
        review.setContent(request.content() == null ? "" : request.content().trim());
        reviews.save(review);

        notifyCounterparty(order, productId, review);

        return ApiResponse.success(201, "评价已发布", reviewView(review));
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<List<Map<String, Object>>> byOrder(@PathVariable Long orderId, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        TradeOrder order = orders.findById(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该订单评价");
        }
        return ApiResponse.success(reviews.findByOrderId(orderId).stream()
                .map(this::reviewView).toList());
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<Map<String, Object>> byUser(@PathVariable Long userId,
                                                    @RequestParam(defaultValue = "BUYER_REVIEWS_SELLER") String type) {
        ReviewType reviewType;
        try {
            reviewType = ReviewType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "无效的评价类型");
        }
        List<TradeReview> list = reviews.findByRevieweeIdAndReviewTypeOrderByCreatedAtDesc(userId, reviewType);
        double avg = list.isEmpty() ? 0 : list.stream().mapToInt(TradeReview::getRating).average().orElse(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", list.size());
        result.put("averageRating", Math.round(avg * 10.0) / 10.0);
        result.put("reviews", list.stream().map(this::reviewView).toList());
        return ApiResponse.success(result);
    }

    private void notifyCounterparty(TradeOrder order, Long productId, TradeReview review) {
        Conversation conversation = conversations.findByProductIdAndBuyerId(productId, order.getBuyerId())
                .orElseGet(() -> {
                    Conversation created = new Conversation();
                    created.setProductId(productId);
                    created.setBuyerId(order.getBuyerId());
                    created.setSellerId(order.getSellerId());
                    return conversations.save(created);
                });
        String stars = "★".repeat(review.getRating()) + "☆".repeat(5 - review.getRating());
        String role = review.getReviewType() == ReviewType.BUYER_REVIEWS_SELLER ? "买家评价" : "卖家评价";
        String content = String.format("[[REVIEW:%d]]%s｜%s\n%s",
                review.getRating(), role, stars,
                review.getContent() == null || review.getContent().isBlank() ? "（未填写评价内容）" : review.getContent());
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderId(review.getReviewerId());
        message.setContent(content);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversations.save(conversation);
        messages.save(message);
    }

    private Map<String, Object> reviewView(TradeReview review) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", review.getId());
        result.put("orderId", review.getOrderId());
        result.put("productId", review.getProductId());
        result.put("reviewerId", review.getReviewerId());
        result.put("revieweeId", review.getRevieweeId());
        result.put("reviewType", review.getReviewType().name());
        result.put("rating", review.getRating());
        result.put("content", review.getContent());
        result.put("reviewerName", users.findById(review.getReviewerId())
                .map(AppUser::getDisplayName).orElse("校园用户"));
        result.put("reviewerAvatar", users.findById(review.getReviewerId())
                .map(AppUser::getAvatarUrl).orElse(null));
        result.put("createdAt", review.getCreatedAt());
        return result;
    }

    public record CreateReviewRequest(
            @NotNull Long orderId,
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 500) String content) {}
}
