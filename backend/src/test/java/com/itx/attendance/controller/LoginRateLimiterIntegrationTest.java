package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.dto.request.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-011 (R-001, SEC score 6): LoginRateLimitFilter.extractClientIp() must key on
 * request.getRemoteAddr(), never on a client-supplied X-Forwarded-For header — otherwise
 * an attacker bypasses the 5-attempts/min/IP budget by varying the header on every request.
 *
 * Shares the {@code sharedctxd} context/DB with AuthControllerTest, AuthPasswordFlowTest,
 * and RequestRejectFlakeReproTest (see AbstractIntegrationTest); relies on that base
 * class's {@code resetTestState()} clearing LoginRateLimitFilter's in-memory bucket
 * before this test method, not on having its own private context.
 */
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:sharedctxd;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR"
})
class LoginRateLimiterIntegrationTest extends AbstractIntegrationTest {

    @Test
    void login_spoofedForwardedForHeader_doesNotBypassPerIpRateLimit() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("nonexistent", "wrong"));

        // MockMvc's request.getRemoteAddr() ignores request headers entirely — there is no
        // RemoteIpValve in this dispatch path, so every call below is the same real "IP" no
        // matter what X-Forwarded-For claims, exactly mirroring the production guarantee that
        // only Tomcat's trusted-proxy-checked getRemoteAddr() is read, never the raw header.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "10.0.0." + i)
                    .content(body))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "10.0.0.99")
                .content(body))
            .andExpect(status().is(429))
            .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
    }
}
