package com.campus.trade.dto.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productTitle,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal
) {
}
