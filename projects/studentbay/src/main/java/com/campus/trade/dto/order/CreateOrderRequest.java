package com.campus.trade.dto.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Long sellerId,
        @NotBlank @Size(max = 200) String productTitle,
        @NotNull @DecimalMin("0.01") BigDecimal unitPrice,
        @NotNull @Min(1) Integer quantity,
        @Size(max = 30) String shippingMethod,
        @Size(max = 500) String shippingAddress,
        @DecimalMin("0.00") BigDecimal shippingCost
) {
}
