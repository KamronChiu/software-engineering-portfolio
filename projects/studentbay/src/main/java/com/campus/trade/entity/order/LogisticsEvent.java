package com.campus.trade.entity.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "logistics_events", indexes = @Index(name = "idx_logistics_order_time", columnList = "order_id,occurred_at"))
@Getter
@Setter
@NoArgsConstructor
public class LogisticsEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 160)
    private String location;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
