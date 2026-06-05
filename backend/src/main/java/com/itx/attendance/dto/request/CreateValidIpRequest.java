package com.itx.attendance.dto.request;

import com.itx.attendance.domain.IpScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateValidIpRequest(
    @NotBlank String ipAddress,
    @NotNull IpScope scope,
    String employeeId,
    @Size(max = 255) String description
) {}
