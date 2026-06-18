package com.itx.attendance.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record OfficeLocationDto(
    Long id,
    String name,
    BigDecimal latitude,
    BigDecimal longitude,
    int radiusMeters,
    boolean active,
    LocalDateTime createdAt
) {}
