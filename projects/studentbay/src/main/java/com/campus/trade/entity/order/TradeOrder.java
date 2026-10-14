package com.campus.trade.entity.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_buyer", columnList = "buyer_id"),
        @Index(name = "idx_orders_seller", columnList = "seller_id")
})
@Getter
@Setter
@NoArgsConstructor
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "product_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal productAmount;

    @Column(name = "shipping_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "coin_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal coinDiscountAmount = BigDecimal.ZERO;

    @Column(name = "coins_used", nullable = false)
    private int coinsUsed;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "shipping_method", length = 30)
    private String shippingMethod;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @Column(name = "recipient_name", length = 60)
    private String recipientName;

    @Column(name = "recipient_phone", length = 30)
    private String recipientPhone;

    @Column(name = "coupon_code", length = 30)
    private String couponCode;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "payment_idempotency_key", unique = true, length = 80)
    private String paymentIdempotencyKey;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "payment_expires_at")
    private LocalDateTime paymentExpiresAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 160)
    private String cancellationReason;

    @Column(name = "price_adjusted_at")
    private LocalDateTime priceAdjustedAt;

    @Column(name = "original_product_amount", precision = 12, scale = 2)
    private BigDecimal originalProductAmount;

    @Column(name = "original_shipping_cost", precision = 12, scale = 2)
    private BigDecimal originalShippingCost;

    @Column(name = "price_adjustment_count", nullable = false)
    private int priceAdjustmentCount;

    @Column(name = "courier_company", length = 80)
    private String courierCompany;

    @Column(name = "courier_name", length = 60)
    private String courierName;

    @Column(name = "courier_phone", length = 40)
    private String courierPhone;

    @Column(name = "pickup_time", length = 80)
    private String pickupTime;

    @Column(name = "tracking_number", length = 80)
    private String trackingNumber;

    @Column(name = "pickup_code", length = 20)
    private String pickupCode;

    @Column(name = "pickup_location", length = 160)
    private String pickupLocation;

    @Column(name = "pickup_meeting_start_at")
    private LocalDateTime pickupMeetingStartAt;

    @Column(name = "pickup_meeting_end_at")
    private LocalDateTime pickupMeetingEndAt;

    @Column(name = "buyer_pickup_code", length = 12)
    private String buyerPickupCode;

    @Column(name = "seller_pickup_code", length = 12)
    private String sellerPickupCode;

    @Column(name = "seller_handover_confirmed", nullable = false)
    private boolean sellerHandoverConfirmed;

    @Column(name = "buyer_receipt_confirmed", nullable = false)
    private boolean buyerReceiptConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_status", length = 30)
    private ShipmentStatus shipmentStatus;

    @Column(name = "estimated_delivery_at")
    private LocalDateTime estimatedDeliveryAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

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
