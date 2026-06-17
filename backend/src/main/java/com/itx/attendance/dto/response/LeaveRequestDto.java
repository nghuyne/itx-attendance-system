package com.itx.attendance.dto.response;

import com.itx.attendance.domain.LeaveType;
import com.itx.attendance.domain.RequestStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record LeaveRequestDto(
        Long id,
        String employeeId,
        LeaveType leaveType,
        LocalDate startDate,
        LocalDate endDate,
        int totalDays,
        String reason,
        RequestStatus status,
        String approverId,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
