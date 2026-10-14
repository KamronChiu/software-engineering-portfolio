package com.campus.trade.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InterventionRequest(@NotBlank @Size(max = 1000) String reason) {}
