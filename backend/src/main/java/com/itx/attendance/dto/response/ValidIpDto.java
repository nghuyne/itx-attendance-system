package com.itx.attendance.dto.response;

import com.itx.attendance.domain.IpScope;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ValidIpDto(
    Long id,
    String ipAddress,
    IpScope scope,
    String employeeId,
    String employeeName,
    String description,
    LocalDateTime createdAt
) {}
