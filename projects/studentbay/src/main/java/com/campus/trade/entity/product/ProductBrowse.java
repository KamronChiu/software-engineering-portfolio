package com.campus.trade.entity.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 最近浏览/足迹（T4.4）：登录用户浏览详情自动记录，一用户一商品一行，重复浏览只刷新时间 */
@Entity
@Table(name = "product_browse", uniqueConstraints = {
        @UniqueConstraint(name = "uk_browse_user_product", columnNames = {"user_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class ProductBrowse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}
