package com.itx.attendance.dto.response;

import com.itx.attendance.domain.ApprovalSubStatus;
import com.itx.attendance.domain.AttendanceStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record AdminAttendanceRecordDto(
    String id,
    String employeeId,
    String employeeName,
    String shiftId,
    String shiftName,
    LocalDate date,
    LocalDateTime checkInTime,
    LocalDateTime checkOutTime,
    String checkInPhotoUrl,
    String checkOutPhotoUrl,
    AttendanceStatus attendanceStatus,
    ApprovalSubStatus approvalSubStatus,
    boolean isAdminOverride,
    Long version,
    LocalDateTime createdAt
) {}
