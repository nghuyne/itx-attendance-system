package com.itx.attendance.dto.response;

import com.itx.attendance.domain.HolidayType;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record HolidayDto(
    Long id,
    LocalDate date,
    String name,
    HolidayType type,
    Integer year,
    LocalDateTime createdAt
) {}
