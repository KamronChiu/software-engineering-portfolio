package com.campus.trade.entity.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "trade_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_order_reviewer", columnNames = {"order_id", "reviewer_id"}),
        indexes = {
                @Index(name = "idx_review_reviewee", columnList = "reviewee_id,review_type"),
                @Index(name = "idx_review_order", columnList = "order_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class TradeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 30)
    private ReviewType reviewType;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
