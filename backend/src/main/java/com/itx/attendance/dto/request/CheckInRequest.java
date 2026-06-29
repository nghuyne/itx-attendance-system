package com.itx.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(
    Double lat,
    Double lng,
    @NotBlank String photoBase64,
    boolean isClientSite,
    String bssid
) {}
