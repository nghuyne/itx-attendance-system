package com.itx.attendance.dto.response;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record ValidMacDto(
    Long id,
    String bssid,
    String description,
    String createdBy,
    LocalDateTime createdAt
) {}
