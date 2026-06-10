package com.itx.attendance.dto.response;

import com.itx.attendance.domain.ApprovalSubStatus;
import com.itx.attendance.domain.AttendanceStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Builder
public record TeamRosterItemDto(
        String employeeId,
        String employeeName,
        String shiftId,
        String shiftName,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        AttendanceStatus attendanceStatus,
        ApprovalSubStatus approvalSubStatus,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        boolean hasPendingRequest,
        String pendingRequestId,
        String pendingRequestCategory
) {}
