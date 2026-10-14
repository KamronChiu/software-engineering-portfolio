package com.campus.trade.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourierBookingRequest(
        @NotBlank @Size(max = 80) String courierCompany,
        @NotBlank @Size(max = 80) String pickupTime
) {
}
