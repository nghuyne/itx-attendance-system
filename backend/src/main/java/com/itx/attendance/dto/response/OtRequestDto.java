package com.itx.attendance.dto.response;

import com.itx.attendance.domain.RequestStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record OtRequestDto(
        String id,
        String employeeId,
        String employeeName,
        LocalDate plannedDate,
        BigDecimal plannedOtHours,
        String reason,
        RequestStatus status,
        String approverId,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
