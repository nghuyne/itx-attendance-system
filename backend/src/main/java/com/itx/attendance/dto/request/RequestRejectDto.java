package com.itx.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RequestRejectDto(
        @NotBlank String reason
) {}
