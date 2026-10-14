package com.campus.trade.entity.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_seller", columnList = "seller_id"),
        @Index(name = "idx_product_status_category", columnList = "status,category")
})
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "item_condition", nullable = false, length = 40)
    private String condition;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "image_url", length = 800)
    private String imageUrl;

    @Column(name = "location_name", length = 160)
    private String locationName;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /** 多图列表，第 1 张为主图（独立表 product_images） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", length = 800)
    private List<String> images = new ArrayList<>();

    /** 是否开启砍一刀 */
    @Column(name = "cut_enabled")
    private Boolean cutEnabled;

    @Column(name = "cut_min", precision = 8, scale = 2)
    private java.math.BigDecimal cutMin;

    @Column(name = "cut_max", precision = 8, scale = 2)
    private java.math.BigDecimal cutMax;

    /** 最多可被砍次数 */
    @Column(name = "cut_limit")
    private Integer cutLimit;

    /** 已砍次数 */
    @Column(name = "cut_count")
    private Integer cutCount;

    /** 关联校园 POI id（自定义位置为 null） */
    @Column(name = "poi_id")
    private Long poiId;

    /** 详细地址/地点描述 */
    @Column(name = "address_detail", length = 300)
    private String addressDetail;

    /** 结构化交易地址：国家 / 省份 / 城市 / 区县 / 街道园区 */
    @Column(name = "address_country", length = 40)
    private String addressCountry;

    @Column(name = "address_province", length = 60)
    private String addressProvince;

    @Column(name = "address_city", length = 60)
    private String addressCity;

    @Column(name = "address_district", length = 60)
    private String addressDistrict;

    @Column(name = "address_street", length = 120)
    private String addressStreet;

    // ---------- 阶段 4：商品参数 / 成色 / 议价（T4.3 / T4.7） ----------
    @Column(length = 80)
    private String brand;

    @Column(length = 80)
    private String size;

    @Column(length = 80)
    private String model;

    @Column(length = 80)
    private String material;

    @Column(length = 40)
    private String color;

    @Column(length = 20)
    private String gender;

    @Column(name = "sport_type", length = 40)
    private String sportType;

    /** 成色/瑕疵说明（闲鱼式"使用情况"） */
    @Column(name = "condition_detail", length = 500)
    private String conditionDetail;

    /** 教材 ISBN（支持搜索） */
    @Column(length = 32)
    private String isbn;

    /** 课程编号（支持搜索） */
    @Column(name = "course_code", length = 60)
    private String courseCode;

    /** 课程/模块名称（支持搜索） */
    @Column(name = "module_name", length = 120)
    private String moduleName;

    /** 是否可议价（议价=可协商标记，与"砍一刀"独立） */
    @Column(name = "bargain_enabled")
    private Boolean bargainEnabled;

    /** 置顶到期时间（null=未置顶，到期自动失效） */
    @Column(name = "top_expire_at")
    private java.time.LocalDateTime topExpireAt;

    /** 擦亮时间（用于"擦亮优先"排序权重） */
    @Column(name = "sharpened_at")
    private java.time.LocalDateTime sharpenedAt;

    @Column(name = "auto_reply_enabled")
    private Boolean autoReplyEnabled;

    @Column(name = "auto_reply_text", length = 500)
    private String autoReplyText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.AVAILABLE;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

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
