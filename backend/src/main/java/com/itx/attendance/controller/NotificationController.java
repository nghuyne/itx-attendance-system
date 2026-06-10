package com.itx.attendance.controller;

import com.itx.attendance.domain.Notification;
import com.itx.attendance.domain.User;
import com.itx.attendance.dto.response.NotificationDto;
import com.itx.attendance.dto.response.NotificationPendingResponse;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.NotificationRepository;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @GetMapping("/pending")
    public NotificationPendingResponse getPending() {
        User user = resolveCurrentUser();
        List<Notification> unread = notificationRepository.findByRecipientIdAndIsReadFalse(user.getId());
        List<NotificationDto> dtos = unread.stream().map(this::toDto).toList();
        return new NotificationPendingResponse(dtos, dtos.size());
    }

    @PutMapping("/{id}/read")
    public NotificationDto markAsRead(@PathVariable String id) {
        User user = resolveCurrentUser();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Notification not found", HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND"));

        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new BusinessException("Forbidden: notification does not belong to current user", HttpStatus.FORBIDDEN, "NOTIFICATION_FORBIDDEN");
        }

        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        return toDto(saved);
    }

    @PutMapping("/read-all")
    public Map<String, Long> markAllAsRead() {
        User user = resolveCurrentUser();
        int updatedCount = notificationRepository.markAllAsReadByRecipientId(user.getId());
        return Map.of("updatedCount", (long) updatedCount);
    }

    private User resolveCurrentUser() {
        String username = SecurityUtil.getCurrentUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .recipientId(n.getRecipient().getId())
                .type(n.getType())
                .referenceId(n.getReferenceId())
                .message(n.getMessage())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
