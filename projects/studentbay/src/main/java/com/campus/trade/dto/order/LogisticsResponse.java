package com.campus.trade.dto.order;

import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.order.ShipmentStatus;

import java.time.LocalDateTime;
import java.util.List;

public record LogisticsResponse(
        Long orderId,
        String orderNo,
        String productTitle,
        OrderStatus orderStatus,
        ShipmentStatus shipmentStatus,
        String courierCompany,
        String courierName,
        String courierPhone,
        String pickupTime,
        String pickupCode,
        String trackingNumber,
        LocalDateTime estimatedDeliveryAt,
        String shippingAddress,
        boolean sellerView,
        List<LogisticsEventResponse> events
) {
}
