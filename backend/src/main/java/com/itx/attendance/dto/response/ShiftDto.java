package com.itx.attendance.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Builder
public record ShiftDto(
    String id,
    String name,
    @JsonFormat(pattern = "HH:mm") LocalTime startTime,
    @JsonFormat(pattern = "HH:mm") LocalTime endTime,
    int checkInOpenMinutes,
    int lateInThreshold,
    int earlyOutThreshold,
    int halfDayThreshold,
    int otBuffer,
    long assignedCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
