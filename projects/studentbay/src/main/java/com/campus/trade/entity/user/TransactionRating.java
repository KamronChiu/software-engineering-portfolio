package com.campus.trade.entity.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_ratings",
        uniqueConstraints = @UniqueConstraint(name = "uk_rating_order_rater", columnNames = {"order_id", "rater_id"}),
        indexes = @Index(name = "idx_rating_target", columnList = "target_user_id,created_at"))
@Getter
@Setter
@NoArgsConstructor
public class TransactionRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "rater_id", nullable = false)
    private Long raterId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(nullable = false)
    private int score;

    @Column(length = 300)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
