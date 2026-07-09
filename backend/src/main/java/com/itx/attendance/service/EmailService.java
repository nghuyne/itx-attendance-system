package com.itx.attendance.service;

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
    @Async("taskExecutor")
    public void sendEmailAsync(String recipientId, String email, String subject, String body) {
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
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return "***";
        return email.substring(0, 2) + "***" + email.substring(at);
    }
}
