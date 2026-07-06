package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.Department;
import com.itx.attendance.domain.Shift;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.CreateDepartmentRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 9 — Story 9.1: Department-based Bulk Shift Assignment
 *
 * Tests CRUD on departments, DEPARTMENT_HAS_EMPLOYEES/DEPARTMENT_EMPTY guards,
 * bulk shift assignment with audit log trail, and employee-department assignment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:departmentintegtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.password=test",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "minio.access-key=minioadmin",
    "minio.secret-key=minioadmin",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000"
})
class DepartmentControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;
    private String employeeToken;

    @BeforeEach
    void setUp() throws Exception {
        // AuditLogRepository.deleteAll() is intentionally unsupported (audit logs are immutable);
        // truncate via raw SQL so admin_id FK doesn't block user cleanup between tests.
        jdbcTemplate.execute("DELETE FROM audit_logs");
        userRepository.deleteAll();
        departmentRepository.deleteAll();
        shiftRepository.deleteAll();

        userRepository.save(User.builder()
            .username("dept_admin")
            .email("dept_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Department Admin")
            .role(UserRole.ADMIN)
            .build());
        userRepository.save(User.builder()
            .username("dept_employee")
            .email("dept_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Department Employee")
            .role(UserRole.EMPLOYEE)
            .build());

        adminToken = loginAndGetToken("dept_admin", "admin123");
        employeeToken = loginAndGetToken("dept_employee", "emp123");
    }

    // ── GET /api/admin/departments ──────────────────────────────────────────

    @Test
    void getDepartments_emptyDb_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/admin/departments")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getDepartments_withEmployees_returnsEmployeeCount() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Kỹ thuật").build());
        userRepository.save(User.builder()
            .username("kt_emp")
            .email("kt_emp@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("KT Employee")
            .role(UserRole.EMPLOYEE)
            .department(dept)
            .build());

        mockMvc.perform(get("/api/admin/departments")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Kỹ thuật"))
            .andExpect(jsonPath("$[0].employeeCount").value(1));
    }

    @Test
    void getDepartments_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/departments")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    // ── POST /api/admin/departments ─────────────────────────────────────────

    @Test
    void createDepartment_validRequest_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateDepartmentRequest("Kinh doanh", "Phòng kinh doanh"));

        mockMvc.perform(post("/api/admin/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("Kinh doanh"))
            .andExpect(jsonPath("$.description").value("Phòng kinh doanh"))
            .andExpect(jsonPath("$.employeeCount").value(0));
    }

    @Test
    void createDepartment_duplicateName_returns409WithDEPARTMENT_ALREADY_EXISTS() throws Exception {
        departmentRepository.save(Department.builder().name("Nhân sự").build());
        String body = objectMapper.writeValueAsString(new CreateDepartmentRequest("Nhân sự", null));

        mockMvc.perform(post("/api/admin/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("DEPARTMENT_ALREADY_EXISTS"));
    }

    @Test
    void createDepartment_missingName_returns400() throws Exception {
        String body = "{\"description\":\"Không có tên\"}";

        mockMvc.perform(post("/api/admin/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    // ── PUT /api/admin/departments/{id} ─────────────────────────────────────

    @Test
    void updateDepartment_validRequest_returns200() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Marketing").build());
        String body = objectMapper.writeValueAsString(new CreateDepartmentRequest("Marketing (Cập nhật)", "Mô tả mới"));

        mockMvc.perform(put("/api/admin/departments/" + dept.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Marketing (Cập nhật)"))
            .andExpect(jsonPath("$.description").value("Mô tả mới"));
    }

    @Test
    void updateDepartment_duplicateNameOnAnother_returns409() throws Exception {
        departmentRepository.save(Department.builder().name("Phòng A").build());
        Department deptB = departmentRepository.save(Department.builder().name("Phòng B").build());
        String body = objectMapper.writeValueAsString(new CreateDepartmentRequest("Phòng A", null));

        mockMvc.perform(put("/api/admin/departments/" + deptB.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("DEPARTMENT_ALREADY_EXISTS"));
    }

    @Test
    void updateDepartment_notFound_returns404WithDEPARTMENT_NOT_FOUND() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateDepartmentRequest("Ghost", null));

        mockMvc.perform(put("/api/admin/departments/9999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("DEPARTMENT_NOT_FOUND"));
    }

    // ── DELETE /api/admin/departments/{id} ──────────────────────────────────

    @Test
    void deleteDepartment_noEmployees_returns204() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Phòng Trống").build());

        mockMvc.perform(delete("/api/admin/departments/" + dept.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteDepartment_hasEmployees_returns400WithDEPARTMENT_HAS_EMPLOYEES() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Phòng Có NV").build());
        userRepository.save(User.builder()
            .username("has_dept_emp")
            .email("has_dept_emp@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Has Dept Employee")
            .role(UserRole.EMPLOYEE)
            .department(dept)
            .build());

        mockMvc.perform(delete("/api/admin/departments/" + dept.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("DEPARTMENT_HAS_EMPLOYEES"));
    }

    @Test
    void deleteDepartment_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/admin/departments/9999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("DEPARTMENT_NOT_FOUND"));
    }

    // ── PUT /api/admin/departments/{id}/shift (bulk assign) ─────────────────

    @Test
    void assignShiftToDepartment_validRequest_updatesAllEmployeesAndReturnsCount() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Phòng Gán Ca").build());
        Shift oldShift = shiftRepository.save(Shift.builder()
            .name("Ca Cũ").shiftStartTime(LocalTime.of(7, 0)).shiftEndTime(LocalTime.of(16, 0)).build());
        Shift newShift = shiftRepository.save(Shift.builder()
            .name("Ca Mới").shiftStartTime(LocalTime.of(8, 0)).shiftEndTime(LocalTime.of(17, 0)).build());

        userRepository.save(User.builder()
            .username("bulk_emp1").email("bulk_emp1@itx.local")
            .passwordHash(passwordEncoder.encode("emp123")).fullName("Bulk Emp 1")
            .role(UserRole.EMPLOYEE).department(dept).shift(oldShift).build());
        userRepository.save(User.builder()
            .username("bulk_emp2").email("bulk_emp2@itx.local")
            .passwordHash(passwordEncoder.encode("emp123")).fullName("Bulk Emp 2")
            .role(UserRole.EMPLOYEE).department(dept).shift(oldShift).build());

        String body = "{\"shiftId\":\"" + newShift.getId() + "\"}";

        mockMvc.perform(put("/api/admin/departments/" + dept.getId() + "/shift")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCount").value(2));

        var updatedEmployees = userRepository.findByDepartmentIdAndActiveTrue(dept.getId());
        updatedEmployees.forEach(u -> assertEquals(newShift.getId(), u.getShift().getId()));

        var auditPage = auditLogRepository.findByFilters(
            null, "users", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(5),
            PageRequest.of(0, 10));
        long bulkAssignEntries = auditPage.getContent().stream()
            .filter(log -> "Department bulk shift assignment".equals(log.getReason()))
            .count();
        assertEquals(2, bulkAssignEntries);
    }

    @Test
    void assignShiftToDepartment_emptyDepartment_returns400WithDEPARTMENT_EMPTY() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Phòng Không NV").build());
        Shift shift = shiftRepository.save(Shift.builder()
            .name("Ca X").shiftStartTime(LocalTime.of(8, 0)).shiftEndTime(LocalTime.of(17, 0)).build());

        String body = "{\"shiftId\":\"" + shift.getId() + "\"}";

        mockMvc.perform(put("/api/admin/departments/" + dept.getId() + "/shift")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("DEPARTMENT_EMPTY"));
    }

    @Test
    void assignShiftToDepartment_shiftNotFound_returns404WithSHIFT_NOT_FOUND() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Phòng Ca Ghost").build());
        userRepository.save(User.builder()
            .username("ghost_shift_emp").email("ghost_shift_emp@itx.local")
            .passwordHash(passwordEncoder.encode("emp123")).fullName("Ghost Shift Emp")
            .role(UserRole.EMPLOYEE).department(dept).build());

        String body = "{\"shiftId\":\"nonexistent-shift-id\"}";

        mockMvc.perform(put("/api/admin/departments/" + dept.getId() + "/shift")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("SHIFT_NOT_FOUND"));
    }

    @Test
    void assignShiftToDepartment_departmentNotFound_returns404WithDEPARTMENT_NOT_FOUND() throws Exception {
        Shift shift = shiftRepository.save(Shift.builder()
            .name("Ca Y").shiftStartTime(LocalTime.of(8, 0)).shiftEndTime(LocalTime.of(17, 0)).build());
        String body = "{\"shiftId\":\"" + shift.getId() + "\"}";

        mockMvc.perform(put("/api/admin/departments/9999/shift")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("DEPARTMENT_NOT_FOUND"));
    }

    // ── GET /api/admin/employees/details ─────────────────────────────────────

    @Test
    void getEmployeesWithDept_returnsShiftAndDepartmentNames() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Phòng Chi Tiết").build());
        Shift shift = shiftRepository.save(Shift.builder()
            .name("Ca Chi Tiết").shiftStartTime(LocalTime.of(8, 0)).shiftEndTime(LocalTime.of(17, 0)).build());
        userRepository.save(User.builder()
            .username("detail_emp").email("detail_emp@itx.local")
            .passwordHash(passwordEncoder.encode("emp123")).fullName("Detail Emp")
            .role(UserRole.EMPLOYEE).department(dept).shift(shift).build());

        mockMvc.perform(get("/api/admin/employees/details")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.username=='detail_emp')].departmentName").value(hasItem("Phòng Chi Tiết")))
            .andExpect(jsonPath("$[?(@.username=='detail_emp')].shiftName").value(hasItem("Ca Chi Tiết")));
    }

    // ── PUT /api/admin/employees/{userId}/department ────────────────────────

    @Test
    void assignEmployeeDepartment_validDepartment_returns200() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Phòng Đích").build());
        User employee = userRepository.save(User.builder()
            .username("assign_dept_emp").email("assign_dept_emp@itx.local")
            .passwordHash(passwordEncoder.encode("emp123")).fullName("Assign Dept Emp")
            .role(UserRole.EMPLOYEE).build());

        String body = "{\"departmentId\":" + dept.getId() + "}";

        mockMvc.perform(put("/api/admin/employees/" + employee.getId() + "/department")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.departmentId").value(dept.getId()))
            .andExpect(jsonPath("$.departmentName").value("Phòng Đích"));
    }

    @Test
    void assignEmployeeDepartment_nullDepartmentId_clearsDepartment() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("Phòng Cũ").build());
        User employee = userRepository.save(User.builder()
            .username("clear_dept_emp").email("clear_dept_emp@itx.local")
            .passwordHash(passwordEncoder.encode("emp123")).fullName("Clear Dept Emp")
            .role(UserRole.EMPLOYEE).department(dept).build());

        mockMvc.perform(put("/api/admin/employees/" + employee.getId() + "/department")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\":null}")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.departmentId").value(nullValue()))
            .andExpect(jsonPath("$.departmentName").value(nullValue()));
    }

    @Test
    void assignEmployeeDepartment_userNotFound_returns404WithUSER_NOT_FOUND() throws Exception {
        mockMvc.perform(put("/api/admin/employees/nonexistent-user-id/department")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\":null}")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
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
