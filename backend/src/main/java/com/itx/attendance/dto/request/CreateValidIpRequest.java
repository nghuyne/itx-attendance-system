package com.itx.attendance.dto.request;

import com.itx.attendance.domain.IpScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateValidIpRequest(
    @NotBlank String ipAddress,
    @NotNull IpScope scope,
    String employeeId,
    String description
) {}
