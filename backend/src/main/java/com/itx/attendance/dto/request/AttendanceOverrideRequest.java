package com.itx.attendance.dto.request;

import com.itx.attendance.domain.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AttendanceOverrideRequest(
    LocalDateTime checkInTime,
    LocalDateTime checkOutTime,
    AttendanceStatus attendanceStatus,
    String photoUrl,
    @NotBlank @Size(min = 10, message = "Lý do phải có ít nhất 10 ký tự") String auditReason
) {}
