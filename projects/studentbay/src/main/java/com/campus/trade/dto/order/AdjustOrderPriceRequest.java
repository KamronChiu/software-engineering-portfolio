package com.campus.trade.dto.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdjustOrderPriceRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal productAmount,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal shippingCost
) {}
