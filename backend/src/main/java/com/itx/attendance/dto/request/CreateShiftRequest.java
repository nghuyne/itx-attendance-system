package com.itx.attendance.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CreateShiftRequest(
    @NotBlank String name,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
    @NotNull @Min(0) Integer checkInOpenMinutes,
    @NotNull @Min(0) Integer lateInThreshold,
    @NotNull @Min(0) Integer earlyOutThreshold,
    @NotNull @Min(0) Integer halfDayThreshold,
    @NotNull @Min(0) Integer otBuffer
) {}
