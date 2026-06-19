package com.itx.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record OtRequestCreateDto(
        @NotNull(message = "Ngày OT không được để trống")
        LocalDate plannedDate,

        @NotNull(message = "Số giờ OT không được để trống")
        BigDecimal plannedOtHours,

        @NotBlank(message = "Lý do không được để trống")
        String reason
) {}
