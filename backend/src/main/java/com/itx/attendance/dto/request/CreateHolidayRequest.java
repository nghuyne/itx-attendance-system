package com.itx.attendance.dto.request;

import com.itx.attendance.domain.HolidayType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CreateHolidayRequest(
    @NotNull LocalDate date,
    @NotBlank @Size(min = 2, max = 255) String name,
    @NotNull HolidayType type,
    @NotNull @Min(1900) @Max(2100) Integer year
) {}
