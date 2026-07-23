package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.PasswordResetTokenRepository;
import com.itx.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:pwflowtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
class AuthPasswordFlowTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder()
            .username("admin")
            .email("admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("System Administrator")
            .role(UserRole.ADMIN)
            .build());
    }

    @Test
    void forgotPassword_withRegisteredEmail_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "admin@itx.local"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void forgotPassword_withUnknownEmail_stillReturns200() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "nobody@itx.local"))))
            .andExpect(status().isOk());
    }

    @Test
    void resetPassword_withInvalidToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "token", "nonexistent-token-xyz",
                    "newPassword", "newpass123"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("TOKEN_INVALID"));
    }

    @Test
    void changePassword_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "oldPassword", "admin123",
                    "newPassword", "newpass456"
                ))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_withCorrectOldPassword_returns200() throws Exception {
        String accessToken = loginAndGetToken("admin", "admin123");

        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "oldPassword", "admin123",
                    "newPassword", "newpass456"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void changePassword_withWrongOldPassword_returns400() throws Exception {
        String accessToken = loginAndGetToken("admin", "admin123");

        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "oldPassword", "wrongpassword",
                    "newPassword", "newpass456"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_OLD_PASSWORD"));
    }
}
