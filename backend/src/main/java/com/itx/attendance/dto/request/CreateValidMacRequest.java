package com.itx.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateValidMacRequest(
    @NotBlank
    @Pattern(regexp = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$",
             message = "BSSID phải đúng định dạng XX:XX:XX:XX:XX:XX")
    String bssid,
    @Size(max = 255) String description
) {}
