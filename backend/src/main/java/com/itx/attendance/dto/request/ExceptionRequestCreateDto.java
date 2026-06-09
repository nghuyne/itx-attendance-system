package com.itx.attendance.dto.request;

import com.itx.attendance.domain.ExceptionRequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExceptionRequestCreateDto(
    @NotBlank String attendanceRecordId,
    @NotNull ExceptionRequestType requestType,
    @NotBlank @Size(min = 10, max = 500) String reason
) {}
