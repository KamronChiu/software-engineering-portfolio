package com.campus.trade.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmShipmentRequest(@NotBlank @Size(max = 80) String trackingNumber) {
}
