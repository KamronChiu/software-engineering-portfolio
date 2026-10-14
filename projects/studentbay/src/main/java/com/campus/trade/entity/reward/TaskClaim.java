package com.campus.trade.entity.reward;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_claims", uniqueConstraints = @UniqueConstraint(
        name = "uk_task_claim_user_code_date", columnNames = {"user_id", "task_code", "claim_date"}))
@Getter
@Setter
@NoArgsConstructor
public class TaskClaim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_code", nullable = false, length = 50)
    private String taskCode;

    @Column(name = "claim_date", nullable = false)
    private LocalDate claimDate;

    @Column(name = "reward_coins", nullable = false)
    private int rewardCoins;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
