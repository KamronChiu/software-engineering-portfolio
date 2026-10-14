package com.campus.trade.dto.order;

import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.order.PaymentStatus;
import com.campus.trade.entity.order.ShipmentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNo,
        Long buyerId,
        Long sellerId,
        OrderStatus status,
        PaymentStatus paymentStatus,
        BigDecimal productAmount,
        BigDecimal shippingCost,
        BigDecimal discountAmount,
        BigDecimal coinDiscountAmount,
        int coinsUsed,
        BigDecimal totalAmount,
        String shippingMethod,
        String shippingAddress,
        String recipientName,
        String recipientPhone,
        String couponCode,
        String paymentMethod,
        LocalDateTime paidAt,
        LocalDateTime paymentExpiresAt,
        LocalDateTime cancelledAt,
        String cancellationReason,
        LocalDateTime priceAdjustedAt,
        String courierCompany,
        String courierName,
        String courierPhone,
        String pickupTime,
        String pickupCode,
        String pickupLocation,
        LocalDateTime pickupMeetingStartAt,
        LocalDateTime pickupMeetingEndAt,
        String myPickupCode,
        boolean sellerHandoverConfirmed,
        boolean buyerReceiptConfirmed,
        String trackingNumber,
        ShipmentStatus shipmentStatus,
        LocalDateTime estimatedDeliveryAt,
        LocalDateTime shippedAt,
        LocalDateTime receivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderItemResponse> items,
        boolean myReviewSubmitted,
        boolean counterpartyReviewSubmitted
) {
}
