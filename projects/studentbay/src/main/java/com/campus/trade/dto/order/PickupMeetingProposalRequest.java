package com.campus.trade.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PickupMeetingProposalRequest(
        @NotNull LocalDateTime meetingStartAt,
        @NotNull LocalDateTime meetingEndAt,
        @NotBlank @Size(max = 160) String location
) {}
