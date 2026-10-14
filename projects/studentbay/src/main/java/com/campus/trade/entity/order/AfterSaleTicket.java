package com.campus.trade.entity.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "after_sale_ticket", indexes = {
        @Index(name = "idx_after_sale_status", columnList = "status"),
        @Index(name = "idx_after_sale_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class AfterSaleTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_no", nullable = false, unique = true, length = 40)
    private String workOrderNo;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AfterSaleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_order_status", nullable = false, length = 30)
    private OrderStatus previousOrderStatus;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(length = 1000)
    private String description;

    @Column(name = "seller_note", length = 1000)
    private String sellerNote;

    @Column(name = "intervention_reason", length = 1000)
    private String interventionReason;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "seller_reviewed_at")
    private LocalDateTime sellerReviewedAt;

    @Column(name = "intervention_at")
    private LocalDateTime interventionAt;

    @Column(name = "admin_reviewed_at")
    private LocalDateTime adminReviewedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

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
