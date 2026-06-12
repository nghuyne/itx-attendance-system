package com.itx.attendance.dto.response;

import com.itx.attendance.domain.RequestStatus;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDateTime;

@Builder
public record AdjustmentRequestDto(
    String id,
    String attendanceRecordId,
    String employeeId,
    Instant proposedCheckoutTime,
    String reason,
    RequestStatus status,
    String reviewedBy,
    String reviewReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
