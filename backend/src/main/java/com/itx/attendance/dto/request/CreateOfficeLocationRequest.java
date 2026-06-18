package com.itx.attendance.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CreateOfficeLocationRequest(
    @NotBlank String name,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
    @NotNull @Min(50) @Max(5000) Integer radiusMeters,
    Boolean isActive
) {}
