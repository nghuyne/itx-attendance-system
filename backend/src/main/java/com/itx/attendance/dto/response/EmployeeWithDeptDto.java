package com.itx.attendance.dto.response;

public record EmployeeWithDeptDto(
    String id,
    String fullName,
    String username,
    String shiftId,
    String shiftName,
    Long departmentId,
    String departmentName
) {}
