package com.campus.trade.entity.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "auto_reply_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_auto_reply_user", columnNames = {"user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AutoReplySetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(length = 500)
    private String content;

    /** T5.4 一次性规则：默认 true（同一买家对同一商品仅自动回复一次）；false 时买家追问可再回一条 */
    @Column(name = "reply_once")
    private Boolean replyOnce = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
