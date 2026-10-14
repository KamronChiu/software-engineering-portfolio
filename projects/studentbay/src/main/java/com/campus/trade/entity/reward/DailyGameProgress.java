package com.campus.trade.entity.reward;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_game_progress", uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_game_user_date", columnNames = {"user_id", "game_date"}))
@Getter
@Setter
@NoArgsConstructor
public class DailyGameProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    @Column(nullable = false)
    private int position;

    @Column(name = "rolls_used", nullable = false)
    private int rollsUsed;

    @Column(name = "coins_earned", nullable = false)
    private int coinsEarned;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
