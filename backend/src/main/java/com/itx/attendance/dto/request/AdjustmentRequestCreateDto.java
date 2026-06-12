package com.itx.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AdjustmentRequestCreateDto(
    @NotBlank String attendanceRecordId,
    @NotNull Instant proposedCheckoutTime,
    @NotBlank @Size(min = 10, max = 500) String reason
) {}
