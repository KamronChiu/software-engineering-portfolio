package com.campus.trade.dto.order;

import jakarta.validation.constraints.NotBlank;

public record PickupProposalDecisionRequest(@NotBlank String decision) {}
