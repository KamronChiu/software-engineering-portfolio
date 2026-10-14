package com.campus.trade.entity.order;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    COURIER_BOOKED,
    SHIPPED,
    COMPLETED,
    RETURN_REQUESTED,
    RETURN_APPROVED,
    INTERVENTION,
    REFUND_APPROVED,
    REFUNDED,
    CANCELLED
}
