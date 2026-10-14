package com.campus.trade.dto.order;

import com.campus.trade.entity.order.ShipmentStatus;

import java.time.LocalDateTime;

public record LogisticsEventResponse(
        Long id,
        ShipmentStatus status,
        String title,
        String description,
        String location,
        LocalDateTime occurredAt
) {
}
