package com.itx.attendance.dto.response;

import lombok.Builder;

@Builder
public record EmployeeDto(
    String id,
    String fullName,
    String username
) {}
