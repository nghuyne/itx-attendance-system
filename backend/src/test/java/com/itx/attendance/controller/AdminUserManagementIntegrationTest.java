package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.Department;
import com.itx.attendance.domain.Shift;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.AuditLogRepository;
import com.itx.attendance.repository.DepartmentRepository;
import com.itx.attendance.repository.ShiftRepository;
import com.itx.attendance.repository.UserRepository;
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

import java.time.LocalTime;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Phase 3 — Admin UI account creation.
 *
 * Covers CRUD-lite user account management: create (with temp password +
 * mustChangePassword), reset password, activate/deactivate, and the paginated
 * user listing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:adminusertestdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.password=test",
    "spring.flyway.enabled=false",
    "app.rate-limit.login.max-attempts=1000",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "minio.access-key=minioadmin",
    "minio.secret-key=minioadmin",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000"
})
class AdminUserManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;
    private String employeeToken;
    private String adminId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("DELETE FROM audit_logs");
        userRepository.deleteAll();
        departmentRepository.deleteAll();
        shiftRepository.deleteAll();

        User admin = userRepository.save(User.builder()
            .username("uam_admin")
            .email("uam_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("User Admin")
            .role(UserRole.ADMIN)
            .build());
        adminId = admin.getId();
        userRepository.save(User.builder()
            .username("uam_employee")
            .email("uam_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("User Employee")
            .role(UserRole.EMPLOYEE)
            .build());

        adminToken = loginAndGetToken("uam_admin", "admin123");
        employeeToken = loginAndGetToken("uam_employee", "emp123");
    }

    // ── POST /api/admin/users ────────────────────────────────────────────────

    @Test
    void createUser_validRequest_returns201WithMustChangePassword() throws Exception {
        String body = """
            {"username":"new_emp","email":"new_emp@itx.local","fullName":"New Employee","role":"EMPLOYEE"}
            """;

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("new_emp"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.mustChangePassword").value(true));

        User created = userRepository.findByUsername("new_emp").orElseThrow();
        assertTrue(created.isMustChangePassword());
        assertTrue(created.isActive());
    }

    @Test
    void createUser_withDepartmentAndShift_returnsDeptAndShiftNames() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Kỹ thuật").build());
        Shift shift = shiftRepository.save(Shift.builder()
            .name("Ca Sáng").shiftStartTime(LocalTime.of(8, 0)).shiftEndTime(LocalTime.of(17, 0)).build());

        String body = objectMapper.writeValueAsString(new Object() {
            public final String username = "dept_emp";
            public final String email = "dept_emp@itx.local";
            public final String fullName = "Dept Employee";
            public final String role = "EMPLOYEE";
            public final Long departmentId = dept.getId();
            public final String shiftId = shift.getId();
        });

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.departmentName").value("Kỹ thuật"))
            .andExpect(jsonPath("$.shiftName").value("Ca Sáng"));
    }

    @Test
    void createUser_duplicateUsername_returns409WithUSERNAME_ALREADY_EXISTS() throws Exception {
        String body = """
            {"username":"uam_employee","email":"another@itx.local","fullName":"Dup","role":"EMPLOYEE"}
            """;

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void createUser_duplicateEmail_returns409WithEMAIL_ALREADY_EXISTS() throws Exception {
        String body = """
            {"username":"brand_new","email":"uam_employee@itx.local","fullName":"Dup Email","role":"EMPLOYEE"}
            """;

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void createUser_departmentNotFound_returns404() throws Exception {
        String body = """
            {"username":"ghost_dept","email":"ghost_dept@itx.local","fullName":"Ghost","role":"EMPLOYEE","departmentId":9999}
            """;

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void createUser_missingRequiredFields_returns400() throws Exception {
        String body = "{\"email\":\"missing@itx.local\"}";

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_nonAdmin_returns403() throws Exception {
        String body = """
            {"username":"blocked","email":"blocked@itx.local","fullName":"Blocked","role":"EMPLOYEE"}
            """;

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    // ── GET /api/admin/users ─────────────────────────────────────────────────

    @Test
    void getUsers_returnsPagedResults() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ── PUT /api/admin/users/{id}/reset-password ────────────────────────────

    @Test
    void resetPassword_validUser_setsMustChangePasswordAndReturns200() throws Exception {
        User employee = userRepository.findByUsername("uam_employee").orElseThrow();
        String oldHash = employee.getPasswordHash();

        mockMvc.perform(put("/api/admin/users/" + employee.getId() + "/reset-password")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        User updated = userRepository.findById(employee.getId()).orElseThrow();
        assertTrue(updated.isMustChangePassword());
        assertFalse(oldHash.equals(updated.getPasswordHash()));
    }

    @Test
    void resetPassword_userNotFound_returns404() throws Exception {
        mockMvc.perform(put("/api/admin/users/nonexistent-id/reset-password")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    @Test
    void resetPassword_nonAdmin_returns403() throws Exception {
        User employee = userRepository.findByUsername("uam_employee").orElseThrow();

        mockMvc.perform(put("/api/admin/users/" + employee.getId() + "/reset-password")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    // ── PUT /api/admin/users/{id}/deactivate & /activate ────────────────────

    @Test
    void deactivateUser_validUser_returns200AndBlocksLogin() throws Exception {
        User employee = userRepository.findByUsername("uam_employee").orElseThrow();

        mockMvc.perform(put("/api/admin/users/" + employee.getId() + "/deactivate")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        String loginBody = objectMapper.writeValueAsString(new LoginRequest("uam_employee", "emp123"));
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivateSelf_returns400WithCANNOT_DEACTIVATE_SELF() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + adminId + "/deactivate")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("CANNOT_DEACTIVATE_SELF"));
    }

    @Test
    void activateUser_previouslyDeactivated_returns200() throws Exception {
        User employee = userRepository.findByUsername("uam_employee").orElseThrow();
        employee.setActive(false);
        userRepository.save(employee);

        mockMvc.perform(put("/api/admin/users/" + employee.getId() + "/activate")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
