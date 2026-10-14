package com.campus.trade.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnRequest(
        @NotBlank @Size(max = 100) String reason,
        @Size(max = 1000) String description
) {}
