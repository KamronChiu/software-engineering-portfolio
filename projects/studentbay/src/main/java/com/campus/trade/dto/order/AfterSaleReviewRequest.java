package com.campus.trade.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AfterSaleReviewRequest(
        @NotBlank String decision,
        @Size(max = 1000) String note
) {}
