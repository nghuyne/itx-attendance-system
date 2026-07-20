package com.itx.attendance.service;

import com.itx.attendance.repository.AuditLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final AuditLogRepository auditLogRepository;

    @Value("${app.mail.from-address}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @PostConstruct
    void validateMailConfig() {
        if (mailUsername == null || mailUsername.isBlank()) {
            log.warn("spring.mail.username is not configured — email sending will fail at runtime");
        }
    }

    // Plain strings only, never a User entity: a proxy crossing this @Async boundary races
    // the caller's Hibernate Session across threads (see hibernate-loadcontexts-flakiness-investigation.md).
    // adminId is the acting admin for admin-initiated sends (create user / reset password) so a
    // failure can be recorded to the audit log; pass null for system/employee-triggered sends
    // (reminders, notifications, forgot-password) where there is no admin actor to attribute it to.
    @Async("taskExecutor")
    public void sendEmailAsync(String adminId, String recipientId, String email, String subject, String body) {
        if (email == null || email.isBlank()) {
            log.warn("Skipping email for recipient {} — email address is blank", recipientId);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(email);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent to {}", maskEmail(email));
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", maskEmail(email), e.getMessage());
            if (adminId != null) {
                recordFailure(adminId, recipientId, subject, e);
            }
        }
    }

    // Best-effort: this write must never throw back into the async executor, and must not
    // touch any entity that could carry a stale Hibernate Session across threads (see above).
    private void recordFailure(String adminId, String recipientId, String subject, Exception e) {
        try {
            auditLogRepository.insertPlain(adminId, "users", recipientId, "email_delivery",
                null, "FAILED", "Gửi email thất bại (" + subject + "): " + e.getMessage());
        } catch (Exception logEx) {
            log.error("Failed to record email failure to audit log for recipient {}: {}",
                recipientId, logEx.getMessage());
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return "***";
        return email.substring(0, 2) + "***" + email.substring(at);
    }
}
