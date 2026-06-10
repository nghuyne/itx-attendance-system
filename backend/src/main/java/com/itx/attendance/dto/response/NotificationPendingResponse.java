package com.itx.attendance.dto.response;

import java.util.List;

public record NotificationPendingResponse(
        List<NotificationDto> notifications,
        long unreadCount
) {
}
