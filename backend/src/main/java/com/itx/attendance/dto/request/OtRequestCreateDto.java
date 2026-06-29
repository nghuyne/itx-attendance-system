package com.itx.attendance.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record OtRequestCreateDto(
        @NotNull(message = "Ngày OT không được để trống")
        LocalDate plannedDate,

        @NotNull(message = "Số giờ OT không được để trống")
        @DecimalMin(value = "0.5", message = "Số giờ OT tối thiểu là 0.5")
        @DecimalMax(value = "8.0", message = "Số giờ OT tối đa là 8.0")
        BigDecimal plannedOtHours,

        @NotBlank(message = "Lý do không được để trống")
        @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
        String reason
) {}
