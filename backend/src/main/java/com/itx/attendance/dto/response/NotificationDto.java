package com.itx.attendance.dto.response;

import com.itx.attendance.domain.NotificationType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record NotificationDto(
        String id,
        String recipientId,
        NotificationType type,
        String referenceId,
        String message,
        boolean isRead,
        LocalDateTime createdAt
) {
}
