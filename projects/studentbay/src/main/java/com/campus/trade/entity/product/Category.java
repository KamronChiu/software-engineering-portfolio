package com.campus.trade.entity.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 商品分类（T4.1）：一级分类驱动首页分类 Tab 与发布页下拉，字段与设计文档 §4.1 一致 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String name;

    /** 父分类 id，一级分类为 null */
    @Column(name = "parent_id")
    private Long parentId;

    /** 分类图标/emoji */
    @Column(length = 200)
    private String icon;

    /** 排序值，小的在前 */
    @Column(nullable = false)
    private Integer sort;

    /** ENABLED / DISABLED */
    @Column(nullable = false, length = 20)
    private String status = "ENABLED";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
