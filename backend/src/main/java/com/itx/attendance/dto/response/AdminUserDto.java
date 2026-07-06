package com.itx.attendance.dto.response;

import com.itx.attendance.domain.UserRole;

import java.time.LocalDateTime;

public record AdminUserDto(
    String id,
    String username,
    String email,
    String fullName,
    UserRole role,
    boolean active,
    boolean mustChangePassword,
    Long departmentId,
    String departmentName,
    String shiftId,
    String shiftName,
    LocalDateTime createdAt
) {}
