package com.itx.attendance.config;

import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Bootstraps the default admin account on first startup instead of shipping a
 * fixed credential in a SQL migration. The account is created only once, and
 * always with must_change_password=true.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap-password:}")
    private String configuredPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername("admin")) {
            return;
        }

        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        String password = generated ? generateRandomPassword() : configuredPassword;

        userRepository.save(User.builder()
                .username("admin")
                .email("admin@itx.local")
                .passwordHash(passwordEncoder.encode(password))
                .fullName("System Administrator")
                .role(UserRole.ADMIN)
                .active(true)
                .mustChangePassword(true)
                .build());

        if (generated) {
            log.warn("Seeded default admin account with a generated one-time password: {}. " +
                    "Log in and change it immediately, or set ADMIN_BOOTSTRAP_PASSWORD before the next deploy.",
                    password);
        } else {
            log.info("Seeded default admin account using ADMIN_BOOTSTRAP_PASSWORD.");
        }
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
