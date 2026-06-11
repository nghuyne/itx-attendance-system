package com.itx.attendance.dto.response;

import com.itx.attendance.domain.ExceptionRequestType;
import com.itx.attendance.domain.RequestStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record RequestSummaryDto(
        String id,
        String requestCategory,
        String employeeId,
        String employeeName,
        String attendanceRecordId,
        LocalDate attendanceDate,
        ExceptionRequestType requestType,
        LocalDateTime proposedCheckoutTime,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        String reason,
        RequestStatus status,
        String reviewedBy,
        String reviewReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
