package com.itx.attendance.dto.response;

import com.itx.attendance.domain.ApprovalSubStatus;
import com.itx.attendance.domain.AttendanceStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record AttendanceRecordDto(
    String id,
    String employeeId,
    String shiftId,
    String shiftName,
    String shiftStartTime,
    String shiftEndTime,
    LocalDate date,
    LocalDateTime checkInTime,
    String checkInIp,
    BigDecimal checkInLat,
    BigDecimal checkInLng,
    String checkInPhotoUrl,
    LocalDateTime checkOutTime,
    String checkOutIp,
    BigDecimal checkOutLat,
    BigDecimal checkOutLng,
    String checkOutPhotoUrl,
    AttendanceStatus attendanceStatus,
    ApprovalSubStatus approvalSubStatus,
    boolean isClientSite,
    boolean gpsUnavailable,
    boolean suspiciousLocation,
    boolean isAdminOverride,
    Long version,
    LocalDateTime createdAt
) {}
