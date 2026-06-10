package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.repository.NotificationRepository;
import com.itx.attendance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendExceptionRequestNotification(ExceptionRequest request) {
        User leader = request.getEmployee().getLeader();
        if (leader == null) {
            log.warn("Employee {} has no leader assigned — skipping notification for request {}",
                    request.getEmployee().getId(), request.getId());
            return;
        }

        String message = buildExceptionRequestMessage(request);
        String subject = "[ITX] Yêu cầu ngoại lệ mới từ " + request.getEmployee().getFullName();

        sendNotificationToRecipient(leader, NotificationType.EXCEPTION_REQUEST, request.getId(), message, subject);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAdjustmentRequestNotification(AdjustmentRequest request) {
        User leader = request.getEmployee().getLeader();
        if (leader == null) {
            log.warn("Employee {} has no leader assigned — skipping leader notification for adjustment request {}",
                    request.getEmployee().getId(), request.getId());
        }

        List<User> admins = userRepository.findByRole(UserRole.ADMIN);

        Set<String> recipientIds = new HashSet<>();
        List<User> recipients = new ArrayList<>();
        if (leader != null && recipientIds.add(leader.getId())) {
            recipients.add(leader);
        }
        for (User admin : admins) {
            if (recipientIds.add(admin.getId())) {
                recipients.add(admin);
            }
        }

        if (recipients.isEmpty()) {
            log.warn("No recipients found for adjustment request {} — skipping notification", request.getId());
            return;
        }

        String message = buildAdjustmentRequestMessage(request);
        String subject = "[ITX] Yêu cầu điều chỉnh mới từ " + request.getEmployee().getFullName();

        for (User recipient : recipients) {
            sendNotificationToRecipient(recipient, NotificationType.ADJUSTMENT_REQUEST, request.getId(), message, subject);
        }
    }

    private void sendNotificationToRecipient(User recipient, NotificationType type, String referenceId,
                                              String message, String subject) {
        if (notificationRepository.existsByRecipientIdAndTypeAndReferenceId(recipient.getId(), type, referenceId)) {
            log.info("Notification already exists for recipientId={}, type={}, referenceId={} — skipping",
                    recipient.getId(), type, referenceId);
            return;
        }

        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .referenceId(referenceId)
                .message(message)
                .build();
        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException e) {
            log.info("Notification already exists (concurrent save) for recipientId={}, type={}, referenceId={} — skipping",
                    recipient.getId(), type, referenceId);
            return;
        }

        emailService.sendEmailAsync(recipient, subject, message);
    }

    private String buildExceptionRequestMessage(ExceptionRequest request) {
        return String.format(
                "Nhân viên %s đã gửi yêu cầu ngoại lệ cho bản ghi ngày %s.\nLoại: %s\nLý do: %s\n\nVui lòng đăng nhập hệ thống ITX để xem xét và phê duyệt.",
                request.getEmployee().getFullName(),
                request.getAttendanceRecord().getDate(),
                request.getRequestType(),
                request.getReason()
        );
    }

    private String buildAdjustmentRequestMessage(AdjustmentRequest request) {
        return String.format(
                "Nhân viên %s đã gửi yêu cầu điều chỉnh cho bản ghi INCOMPLETE ngày %s.\nThời gian check-out đề xuất: %s\nLý do: %s\n\nVui lòng đăng nhập hệ thống ITX để xem xét và phê duyệt.",
                request.getEmployee().getFullName(),
                request.getAttendanceRecord().getDate(),
                request.getProposedCheckoutTime(),
                request.getReason()
        );
    }
}
