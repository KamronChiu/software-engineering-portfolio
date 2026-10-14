package com.campus.trade.controller.trust;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.order.TradeOrder;
import com.campus.trade.entity.user.TransactionRating;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.user.TransactionRatingRepository;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.trust.TrustScoreService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trust")
public class TrustController {
    private final TrustScoreService trustScores;
    private final CurrentUserService currentUser;
    private final OrderRepository orders;
    private final TransactionRatingRepository ratings;

    public TrustController(TrustScoreService trustScores, CurrentUserService currentUser,
                           OrderRepository orders, TransactionRatingRepository ratings) {
        this.trustScores = trustScores;
        this.currentUser = currentUser;
        this.orders = orders;
        this.ratings = ratings;
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<Map<String, Object>> userScore(@PathVariable Long userId) {
        return ApiResponse.success(trustScores.score(userId));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> myScore(HttpSession session) {
        return ApiResponse.success(trustScores.score(currentUser.require(session).getId()));
    }

    @PostMapping("/orders/{orderId}/rating")
    @Transactional
    public ApiResponse<TransactionRating> rate(@PathVariable Long orderId,
                                               @Valid @RequestBody RatingRequest request,
                                               HttpSession session) {
        Long raterId = currentUser.require(session).getId();
        TradeOrder order = orders.findById(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单完成后才可以评价交易对象");
        }
        if (!order.getBuyerId().equals(raterId) && !order.getSellerId().equals(raterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权评价该订单");
        }
        if (ratings.existsByOrderIdAndRaterId(orderId, raterId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "你已经评价过这笔交易");
        }
        TransactionRating rating = new TransactionRating();
        rating.setOrderId(orderId);
        rating.setRaterId(raterId);
        rating.setTargetUserId(order.getBuyerId().equals(raterId) ? order.getSellerId() : order.getBuyerId());
        rating.setScore(request.score());
        rating.setComment(request.comment() == null ? null : request.comment().trim());
        return ApiResponse.success(201, "交易评价已提交", ratings.save(rating));
    }

    public record RatingRequest(@Min(1) @Max(5) int score, @Size(max = 300) String comment) {}
}
