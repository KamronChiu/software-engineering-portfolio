package com.campus.trade.repository.review;

import com.campus.trade.entity.review.ReviewType;
import com.campus.trade.entity.review.TradeReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeReviewRepository extends JpaRepository<TradeReview, Long> {
    Optional<TradeReview> findByOrderIdAndReviewerId(Long orderId, Long reviewerId);

    List<TradeReview> findByRevieweeIdAndReviewTypeOrderByCreatedAtDesc(Long revieweeId, ReviewType reviewType);

    List<TradeReview> findByRevieweeIdOrderByCreatedAtDesc(Long revieweeId);

    List<TradeReview> findByOrderId(Long orderId);

    boolean existsByOrderIdAndReviewerId(Long orderId, Long reviewerId);
}
