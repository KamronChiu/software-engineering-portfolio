package com.campus.trade.entity.activity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户动态：记录用户发布商品（PUBLISH）与商品被买走（SOLD）的时间节点，
 * 供个人空间「动态」时间轴展示。
 */
@Entity
@Table(name = "user_activities", indexes = {
        @Index(name = "idx_activity_user_time", columnList = "user_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class UserActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityType type;

    @Column(name = "product_id")
    private Long productId;

    /** 商品标题快照，避免商品改名/删除后动态文案失真 */
    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
