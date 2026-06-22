package com.itx.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AssignDepartmentShiftRequest(
    @NotBlank String shiftId
) {}
