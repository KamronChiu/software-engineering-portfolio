package com.campus.trade.entity.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 热搜词（T4.2）：记录用户搜索关键词及次数，供首页热搜榜展示 */
@Entity
@Table(name = "hot_keyword")
@Getter
@Setter
@NoArgsConstructor
public class HotKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String keyword;

    @Column(nullable = false)
    private Long count = 0L;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = LocalDateTime.now();
    }
}
