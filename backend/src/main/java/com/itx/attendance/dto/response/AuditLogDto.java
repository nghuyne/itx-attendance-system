package com.itx.attendance.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AuditLogDto(
    Long id,
    String adminId,
    String adminName,
    String targetTable,
    String targetId,
    String fieldChanged,
    String oldValue,
    String newValue,
    String reason,
    LocalDateTime createdAt
) {}
