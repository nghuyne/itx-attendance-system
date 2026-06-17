package com.itx.attendance.dto.response;

import com.itx.attendance.domain.LeaveType;
import lombok.Builder;

@Builder
public record LeaveBalanceDto(
        Long id,
        String employeeId,
        int year,
        LeaveType leaveType,
        int totalDays,
        int usedDays
) {}
