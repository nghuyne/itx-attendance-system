package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies role-based access control across all API controllers.
 *
 * Two mechanisms enforce 403:
 * 1. URL-level: SecurityConfig requestMatchers for /api/admin/** and /api/leader/**
 *    (fires at filter level, before request body parsing)
 * 2. Method-level: @PreAuthorize on controllers for mixed-role endpoints
 *    (handled by GlobalExceptionHandler.handleAccessDenied → 403)
 *
 * POST tests are only included for admin/leader endpoints where URL-level security
 * blocks the request before @Valid body validation runs.
 * For employee-only POST endpoints (check-in, requests), @Valid fires before
 * @PreAuthorize; testing 403 there requires a valid request body and is
 * covered by integration tests that exercise the full happy-path.
 */
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:sharedctxa;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "app.rate-limit.login.max-attempts=1000"
})
class RoleBasedAccessControlTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String leaderToken;
    private String employeeToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();

        userRepository.save(User.builder()
            .username("rbac_admin")
            .email("rbac_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("RBAC Admin")
            .role(UserRole.ADMIN)
            .build());

        userRepository.save(User.builder()
            .username("rbac_leader")
            .email("rbac_leader@itx.local")
            .passwordHash(passwordEncoder.encode("leader123"))
            .fullName("RBAC Leader")
            .role(UserRole.LEADER)
            .build());

        userRepository.save(User.builder()
            .username("rbac_employee")
            .email("rbac_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("RBAC Employee")
            .role(UserRole.EMPLOYEE)
            .build());

        adminToken = loginAndGetToken("rbac_admin", "admin123");
        leaderToken = loginAndGetToken("rbac_leader", "leader123");
        employeeToken = loginAndGetToken("rbac_employee", "emp123");
    }

    // Endpoint × role → expected status, covering URL-level (SecurityConfig requestMatchers)
    // and method-level (@PreAuthorize) enforcement plus the unauthenticated (401) and
    // correct-role (200) cases in one matrix. One row per case previously covered by its
    // own @Test method — same URLs, roles, bodies and expected statuses, just tabulated
    // instead of copy-pasted so a new endpoint is one row, not a new method.
    static Stream<Arguments> roleEndpointMatrix() {
        return Stream.of(
            // ── /api/admin/** — URL-level: requires ADMIN ────────────────────
            Arguments.of("GET", "/api/admin/shifts", null, "employee", 403),
            Arguments.of("GET", "/api/admin/shifts", null, "leader", 403),
            Arguments.of("GET", "/api/admin/valid-ips", null, "employee", 403),
            Arguments.of("GET", "/api/admin/valid-ips", null, "leader", 403),
            Arguments.of("GET", "/api/admin/valid-macs", null, "employee", 403),
            Arguments.of("GET", "/api/admin/valid-macs", null, "leader", 403),
            Arguments.of("GET", "/api/admin/holidays", null, "employee", 403),
            Arguments.of("GET", "/api/admin/holidays", null, "leader", 403),
            Arguments.of("GET", "/api/admin/employees", null, "employee", 403),
            Arguments.of("GET", "/api/admin/employees", null, "leader", 403),
            Arguments.of("GET", "/api/admin/audit-logs?from=2026-01-01&to=2026-12-31", null, "employee", 403),
            Arguments.of("GET", "/api/admin/audit-logs?from=2026-01-01&to=2026-12-31", null, "leader", 403),
            Arguments.of("POST", "/api/admin/shifts", "{}", "employee", 403),
            Arguments.of("POST", "/api/admin/valid-ips", "{}", "leader", 403),
            // ── /api/leader/** — URL-level: requires LEADER or ADMIN ─────────
            Arguments.of("GET", "/api/leader/team-roster", null, "employee", 403),
            // ── /api/attendance/** — method-level @PreAuthorize: EMPLOYEE only
            Arguments.of("GET", "/api/attendance/today", null, "admin", 403),
            Arguments.of("GET", "/api/attendance/today", null, "leader", 403),
            Arguments.of("GET", "/api/attendance/history?from=2026-01-01&to=2026-12-31", null, "admin", 403),
            // ── /api/requests/** — method-level @PreAuthorize: mixed roles ───
            Arguments.of("GET", "/api/requests/pending", null, "employee", 403),
            Arguments.of("PUT", "/api/requests/nonexistent-id/approve", null, "employee", 403),
            Arguments.of("PUT", "/api/requests/nonexistent-id/reject", "{\"reason\":\"test\"}", "employee", 403),
            // ── Positive: correct roles pass auth ────────────────────────────
            Arguments.of("GET", "/api/admin/shifts", null, "admin", 200),
            Arguments.of("GET", "/api/admin/valid-ips", null, "admin", 200),
            Arguments.of("GET", "/api/leader/team-roster", null, "leader", 200),
            Arguments.of("GET", "/api/requests/pending", null, "leader", 200),
            Arguments.of("GET", "/api/requests/pending", null, "admin", 200),
            // ── Unauthenticated → 401 ─────────────────────────────────────────
            Arguments.of("GET", "/api/admin/shifts", null, null, 401),
            Arguments.of("GET", "/api/leader/team-roster", null, null, 401),
            Arguments.of("GET", "/api/attendance/today", null, null, 401)
        );
    }

    @ParameterizedTest(name = "[{index}] {0} {1} as {3} → {4}")
    @MethodSource("roleEndpointMatrix")
    void endpointRoleMatrix(String method, String url, String body, String role, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = switch (method) {
            case "GET" -> get(url);
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
        if (body != null) {
            requestBuilder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (role != null) {
            requestBuilder.header("Authorization", "Bearer " + tokenFor(role));
        }
        mockMvc.perform(requestBuilder).andExpect(status().is(expectedStatus));
    }

    private String tokenFor(String role) {
        return switch (role) {
            case "admin" -> adminToken;
            case "leader" -> leaderToken;
            case "employee" -> employeeToken;
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        };
    }

}
