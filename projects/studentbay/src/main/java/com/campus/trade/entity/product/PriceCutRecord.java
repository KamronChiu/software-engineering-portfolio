package com.campus.trade.entity.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 砍一刀记录：记录每次买家砍价的金额与砍前/砍后价格。
 * (product_id, buyer_id) 唯一，保证每个买家对同一商品只能砍一次。
 */
@Entity
@Table(name = "price_cut_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_price_cut_product_buyer", columnNames = {"product_id", "buyer_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class PriceCutRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal amount;

    @Column(name = "price_before", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceBefore;

    @Column(name = "price_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
