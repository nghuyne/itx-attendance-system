package com.itx.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestRejectDto(
        @NotBlank @Size(max = 1000) String reason
) {}
