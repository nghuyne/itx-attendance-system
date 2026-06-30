package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.LoginRequest;
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
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:roleaccesstestdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000"
})
class RoleBasedAccessControlTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

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

    // ── /api/admin/** — URL-level: requires ADMIN ────────────────────────────

    @Test
    void adminShifts_employee_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/shifts")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminShifts_leader_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/shifts")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminValidIps_employee_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/valid-ips")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminValidIps_leader_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/valid-ips")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminValidMacs_employee_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/valid-macs")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminValidMacs_leader_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/valid-macs")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminHolidays_employee_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/holidays")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminHolidays_leader_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/holidays")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminEmployees_employee_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/employees")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminEmployees_leader_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/employees")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminAuditLogs_employee_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminAuditLogs_leader_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCreateShift_employee_gets403() throws Exception {
        mockMvc.perform(post("/api/admin/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCreateValidIp_leader_gets403() throws Exception {
        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isForbidden());
    }

    // ── /api/leader/** — URL-level: requires LEADER or ADMIN ─────────────────

    @Test
    void leaderTeamRoster_employee_gets403() throws Exception {
        mockMvc.perform(get("/api/leader/team-roster")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    // ── /api/attendance/** — method-level @PreAuthorize: requires EMPLOYEE ───

    @Test
    void attendanceToday_admin_gets403() throws Exception {
        mockMvc.perform(get("/api/attendance/today")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void attendanceToday_leader_gets403() throws Exception {
        mockMvc.perform(get("/api/attendance/today")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void attendanceHistory_admin_gets403() throws Exception {
        mockMvc.perform(get("/api/attendance/history")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden());
    }

    // ── /api/requests/** — method-level @PreAuthorize: mixed roles ───────────

    @Test
    void requestsPending_employee_gets403() throws Exception {
        mockMvc.perform(get("/api/requests/pending")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void requestsApprove_employee_gets403() throws Exception {
        mockMvc.perform(put("/api/requests/nonexistent-id/approve")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void requestsReject_employee_gets403() throws Exception {
        mockMvc.perform(put("/api/requests/nonexistent-id/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"test\"}")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    // ── Positive: correct roles pass auth ────────────────────────────────────

    @Test
    void adminShifts_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/shifts")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminValidIps_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/valid-ips")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void leaderTeamRoster_leader_returns200() throws Exception {
        mockMvc.perform(get("/api/leader/team-roster")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isOk());
    }

    @Test
    void requestsPending_leader_returns200() throws Exception {
        mockMvc.perform(get("/api/requests/pending")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isOk());
    }

    @Test
    void requestsPending_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/requests/pending")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    // ── Unauthenticated → 401 ────────────────────────────────────────────────

    @Test
    void adminShifts_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/shifts"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void leaderTeamRoster_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/leader/team-roster"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void attendanceToday_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/attendance/today"))
            .andExpect(status().isUnauthorized());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
