package com.itx.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CheckOutRequest(
    Double lat,
    Double lng,
    @NotBlank String photoBase64
) {}
