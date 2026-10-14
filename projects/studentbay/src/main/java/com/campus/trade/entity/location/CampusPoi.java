package com.campus.trade.entity.location;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 校园 POI（地点）：发布页"交易地点名称 × 地图"联动的数据源。
 * 预置校园常见地点，选择地点时自动定位地图；点地图时反查最近 POI 回填名称。
 */
@Entity
@Table(name = "campus_pois", indexes = {
        @Index(name = "idx_poi_enabled_sort", columnList = "enabled,sort")
})
@Getter
@Setter
@NoArgsConstructor
public class CampusPoi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 40)
    private String category;

    @Column(nullable = false)
    private int sort;

    @Column(nullable = false)
    private boolean enabled = true;
}
