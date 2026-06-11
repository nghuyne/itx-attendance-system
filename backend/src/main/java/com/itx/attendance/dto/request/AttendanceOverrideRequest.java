package com.itx.attendance.dto.request;

import com.itx.attendance.domain.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AttendanceOverrideRequest(
    LocalDateTime checkInTime,
    LocalDateTime checkOutTime,
    AttendanceStatus attendanceStatus,
    String photoUrl,
    @NotBlank String auditReason
) {}
