package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.*;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 5 — Story 5.2: Immutable Audit Log Viewer (Admin)
 *
 * Covers:
 *   AC-1 — GET /api/admin/audit-logs pagination, sort DESC, filters (adminId, targetTable, date range)
 *   AC-2 — audit_logs immutability enforced at repository level
 *   AC-3 — GET /api/admin/admins returns only ADMIN-role users
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:auditlogtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000"
})
class AuditLogControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;
    private String employeeToken;
    private User adminUser;
    private User secondAdmin;
    private User employee;

    @BeforeEach
    void setUp() throws Exception {
        // AuditLogRepository blocks delete()/deleteAll() (immutable) — truncate via raw SQL.
        jdbcTemplate.execute("DELETE FROM audit_logs");
        userRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
            .username("audit_admin")
            .email("audit_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Audit Admin")
            .role(UserRole.ADMIN)
            .build());

        secondAdmin = userRepository.save(User.builder()
            .username("audit_admin2")
            .email("audit_admin2@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Second Admin")
            .role(UserRole.ADMIN)
            .build());

        employee = userRepository.save(User.builder()
            .username("audit_employee")
            .email("audit_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Audit Employee")
            .role(UserRole.EMPLOYEE)
            .build());

        adminToken = loginAndGetToken("audit_admin", "admin123");
        employeeToken = loginAndGetToken("audit_employee", "emp123");
    }

    // ── GET /api/admin/audit-logs ────────────────────────────────────────────

    @Test
    void getAuditLogs_returnsPaginatedLogsSortedByCreatedAtDesc() throws Exception {
        saveAuditLog(adminUser, "attendance_records", "rec-1", "check_in_time", "old1", "new1", "Lý do sửa lần 1 đủ dài");
        saveAuditLog(adminUser, "attendance_records", "rec-2", "check_out_time", "old2", "new2", "Lý do sửa lần 2 đủ dài");

        mockMvc.perform(get("/api/admin/audit-logs")
                .param("from", "2020-01-01")
                .param("to", "2030-01-01")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[0].fieldChanged").value("check_out_time"))
            .andExpect(jsonPath("$.content[1].fieldChanged").value("check_in_time"))
            .andExpect(jsonPath("$.content[0].adminName").value("Audit Admin"));
    }

    @Test
    void getAuditLogs_filterByAdminId_returnsOnlyMatchingAdmin() throws Exception {
        saveAuditLog(adminUser, "attendance_records", "rec-1", "check_in_time", "old1", "new1", "Lý do sửa lần 1 đủ dài");
        saveAuditLog(secondAdmin, "attendance_records", "rec-2", "check_out_time", "old2", "new2", "Lý do sửa lần 2 đủ dài");

        mockMvc.perform(get("/api/admin/audit-logs")
                .param("from", "2020-01-01")
                .param("to", "2030-01-01")
                .param("adminId", secondAdmin.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].adminName").value("Second Admin"));
    }

    @Test
    void getAuditLogs_filterByTargetTable_returnsOnlyMatchingTable() throws Exception {
        saveAuditLog(adminUser, "attendance_records", "rec-1", "check_in_time", "old1", "new1", "Lý do sửa lần 1 đủ dài");
        saveAuditLog(adminUser, "other_table", "rec-9", "some_field", "old9", "new9", "Lý do sửa lần khác đủ dài");

        mockMvc.perform(get("/api/admin/audit-logs")
                .param("from", "2020-01-01")
                .param("to", "2030-01-01")
                .param("targetTable", "other_table")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].targetTable").value("other_table"));
    }

    @Test
    void getAuditLogs_dateRangeExcludesLogsOutsideWindow() throws Exception {
        saveAuditLog(adminUser, "attendance_records", "rec-1", "check_in_time", "old1", "new1", "Lý do sửa lần 1 đủ dài");

        mockMvc.perform(get("/api/admin/audit-logs")
                .param("from", "2099-01-01")
                .param("to", "2099-01-02")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getAuditLogs_includesAllRequiredFields() throws Exception {
        saveAuditLog(adminUser, "attendance_records", "rec-1", "check_in_time", "08:00", "08:30", "Chỉnh giờ vào cho đúng thực tế");

        mockMvc.perform(get("/api/admin/audit-logs")
                .param("from", "2020-01-01")
                .param("to", "2030-01-01")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].adminId").value(adminUser.getId()))
            .andExpect(jsonPath("$.content[0].adminName").value("Audit Admin"))
            .andExpect(jsonPath("$.content[0].targetTable").value("attendance_records"))
            .andExpect(jsonPath("$.content[0].targetId").value("rec-1"))
            .andExpect(jsonPath("$.content[0].fieldChanged").value("check_in_time"))
            .andExpect(jsonPath("$.content[0].oldValue").value("08:00"))
            .andExpect(jsonPath("$.content[0].newValue").value("08:30"))
            .andExpect(jsonPath("$.content[0].reason").value("Chỉnh giờ vào cho đúng thực tế"))
            .andExpect(jsonPath("$.content[0].createdAt").exists());
    }

    @Test
    void getAuditLogs_nonAdmin_returns403WithForbiddenBody() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("from", "2020-01-01")
                .param("to", "2030-01-01")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void getAuditLogs_missingRequiredDateParams_returns400() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    // ── GET /api/admin/admins ────────────────────────────────────────────────

    @Test
    void getAdmins_returnsOnlyAdminRoleUsers() throws Exception {
        mockMvc.perform(get("/api/admin/admins")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[*].fullName", containsInAnyOrder("Audit Admin", "Second Admin")))
            .andExpect(jsonPath("$[*].fullName", not(hasItem("Audit Employee"))));
    }

    @Test
    void getAdmins_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/admins")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // ── AC-2: Immutability ───────────────────────────────────────────────────

    @Test
    void auditLogRepository_deleteById_throwsUnsupportedOperationException() {
        AuditLog log = saveAuditLog(adminUser, "attendance_records", "rec-1", "check_in_time", "old1", "new1", "Lý do sửa lần 1 đủ dài");

        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> auditLogRepository.deleteById(log.getId()));
    }

    @Test
    void auditLogRepository_deleteAll_throwsUnsupportedOperationException() {
        saveAuditLog(adminUser, "attendance_records", "rec-1", "check_in_time", "old1", "new1", "Lý do sửa lần 1 đủ dài");

        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            auditLogRepository::deleteAll);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AuditLog saveAuditLog(User admin, String targetTable, String targetId,
                                   String fieldChanged, String oldValue, String newValue, String reason) {
        return auditLogRepository.save(
            new AuditLog(admin, targetTable, targetId, fieldChanged, oldValue, newValue, reason));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
