package com.itx.attendance.dto.response;

public record DepartmentDto(
    Long id,
    String name,
    String description,
    long employeeCount
) {}
