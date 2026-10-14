package com.campus.trade.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;

public record CheckoutRequest(
        @NotEmpty List<Long> cartItemIds,
        Long addressId,
        @NotBlank @Size(max = 30) String shippingMethod,
        Long couponId,
        @NotBlank @Size(max = 30) String paymentMethod,
        boolean useCoins,
        LocalDate pickupDate,
        LocalTime pickupStartTime,
        LocalTime pickupEndTime,
        @Size(max = 160) String pickupLocation
) {
}
