package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.Shift;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.CreateShiftRequest;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.ShiftRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 2 — Story 2.1: Fixed Shift Management (Admin)
 *
 * Tests business logic: CRUD operations, validation, SHIFT_IN_USE guard, and assign flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:shiftintegtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000"
})
class ShiftControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        shiftRepository.deleteAll();

        userRepository.save(User.builder()
            .username("shift_admin")
            .email("shift_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Shift Admin")
            .role(UserRole.ADMIN)
            .build());

        adminToken = loginAndGetToken("shift_admin", "admin123");
    }

    // ── GET /api/admin/shifts ────────────────────────────────────────────────

    @Test
    void getShifts_emptyDb_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/admin/shifts")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getShifts_withData_returnsPaginatedList() throws Exception {
        shiftRepository.save(Shift.builder()
            .name("Ca Sáng")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build());
        shiftRepository.save(Shift.builder()
            .name("Ca Chiều")
            .shiftStartTime(LocalTime.of(13, 0))
            .shiftEndTime(LocalTime.of(22, 0))
            .build());

        mockMvc.perform(get("/api/admin/shifts")
                .param("page", "0").param("size", "10")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].name", hasItems("Ca Sáng", "Ca Chiều")));
    }

    // ── POST /api/admin/shifts ───────────────────────────────────────────────

    @Test
    void createShift_validRequest_returns201WithShiftData() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateShiftRequest(
            "Ca Hành Chính", LocalTime.of(8, 0), LocalTime.of(17, 0),
            30, 15, 15, 240, 30));

        mockMvc.perform(post("/api/admin/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("Ca Hành Chính"))
            .andExpect(jsonPath("$.startTime").value("08:00"))
            .andExpect(jsonPath("$.endTime").value("17:00"))
            .andExpect(jsonPath("$.lateInThreshold").value(15))
            .andExpect(jsonPath("$.assignedCount").value(0));
    }

    @Test
    void createShift_startTimeAfterEndTime_returns400WithINVALID_SHIFT_TIME() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateShiftRequest(
            "Ca Sai Gio", LocalTime.of(17, 0), LocalTime.of(8, 0),
            30, 15, 15, 240, 30));

        mockMvc.perform(post("/api/admin/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_SHIFT_TIME"));
    }

    @Test
    void createShift_startTimeEqualsEndTime_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateShiftRequest(
            "Ca Bang Gio", LocalTime.of(8, 0), LocalTime.of(8, 0),
            30, 15, 15, 240, 30));

        mockMvc.perform(post("/api/admin/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_SHIFT_TIME"));
    }

    @Test
    void createShift_duplicateName_returns409WithSHIFT_NAME_EXISTS() throws Exception {
        shiftRepository.save(Shift.builder()
            .name("Ca Sáng")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build());

        String body = objectMapper.writeValueAsString(new CreateShiftRequest(
            "Ca Sáng", LocalTime.of(9, 0), LocalTime.of(18, 0),
            30, 0, 0, 30, 30));

        mockMvc.perform(post("/api/admin/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("SHIFT_NAME_EXISTS"));
    }

    @Test
    void createShift_missingName_returns400() throws Exception {
        String body = "{\"startTime\":\"08:00\",\"endTime\":\"17:00\","
            + "\"checkInOpenMinutes\":30,\"lateInThreshold\":15,"
            + "\"earlyOutThreshold\":15,\"halfDayThreshold\":240,\"otBuffer\":30}";

        mockMvc.perform(post("/api/admin/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createShift_negativeThreshold_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateShiftRequest(
            "Ca Test Neg", LocalTime.of(8, 0), LocalTime.of(17, 0),
            30, -1, 15, 240, 30));

        mockMvc.perform(post("/api/admin/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    // ── PUT /api/admin/shifts/{id} ──────────────────────────────────────────

    @Test
    void updateShift_validRequest_returns200() throws Exception {
        Shift existing = shiftRepository.save(Shift.builder()
            .name("Ca Sáng")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build());

        String body = objectMapper.writeValueAsString(new CreateShiftRequest(
            "Ca Sáng (Cập nhật)", LocalTime.of(8, 30), LocalTime.of(17, 30),
            30, 15, 15, 240, 30));

        mockMvc.perform(put("/api/admin/shifts/" + existing.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Ca Sáng (Cập nhật)"))
            .andExpect(jsonPath("$.startTime").value("08:30"));
    }

    @Test
    void updateShift_startTimeAfterEndTime_returns400() throws Exception {
        Shift existing = shiftRepository.save(Shift.builder()
            .name("Ca Edit Sai")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build());

        String body = objectMapper.writeValueAsString(new CreateShiftRequest(
            "Ca Edit Sai", LocalTime.of(18, 0), LocalTime.of(8, 0),
            30, 15, 15, 240, 30));

        mockMvc.perform(put("/api/admin/shifts/" + existing.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_SHIFT_TIME"));
    }

    @Test
    void updateShift_notFound_returns404WithSHIFT_NOT_FOUND() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateShiftRequest(
            "Ca Ghost", LocalTime.of(8, 0), LocalTime.of(17, 0),
            30, 0, 0, 30, 30));

        mockMvc.perform(put("/api/admin/shifts/nonexistent-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("SHIFT_NOT_FOUND"));
    }

    @Test
    void updateShift_duplicateNameOnAnotherShift_returns409() throws Exception {
        shiftRepository.save(Shift.builder()
            .name("Ca A")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build());
        Shift shiftB = shiftRepository.save(Shift.builder()
            .name("Ca B")
            .shiftStartTime(LocalTime.of(13, 0))
            .shiftEndTime(LocalTime.of(22, 0))
            .build());

        String body = objectMapper.writeValueAsString(new CreateShiftRequest(
            "Ca A", LocalTime.of(13, 0), LocalTime.of(22, 0),
            30, 0, 0, 30, 30));

        mockMvc.perform(put("/api/admin/shifts/" + shiftB.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("SHIFT_NAME_EXISTS"));
    }

    // ── DELETE /api/admin/shifts/{id} ───────────────────────────────────────

    @Test
    void deleteShift_unassigned_returns200() throws Exception {
        Shift shift = shiftRepository.save(Shift.builder()
            .name("Ca Xoa")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build());

        mockMvc.perform(delete("/api/admin/shifts/" + shift.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void deleteShift_assignedToEmployee_returns409WithSHIFT_IN_USE() throws Exception {
        Shift shift = shiftRepository.save(Shift.builder()
            .name("Ca Dang Dung")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build());

        userRepository.save(User.builder()
            .username("emp_with_shift")
            .email("emp_with_shift@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Employee With Shift")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build());

        mockMvc.perform(delete("/api/admin/shifts/" + shift.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("SHIFT_IN_USE"));
    }

    @Test
    void deleteShift_notFound_returns404WithSHIFT_NOT_FOUND() throws Exception {
        mockMvc.perform(delete("/api/admin/shifts/nonexistent-id")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("SHIFT_NOT_FOUND"));
    }

    // ── PUT /api/admin/shifts/{shiftId}/assign/{employeeId} ─────────────────

    @Test
    void assignShift_validEmployee_updatesEmployeeShift() throws Exception {
        Shift shift = shiftRepository.save(Shift.builder()
            .name("Ca Can Gan")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build());
        User employee = userRepository.save(User.builder()
            .username("emp_assign")
            .email("emp_assign@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Employee Assign")
            .role(UserRole.EMPLOYEE)
            .build());

        mockMvc.perform(put("/api/admin/shifts/" + shift.getId() + "/assign/" + employee.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(shift.getId()))
            .andExpect(jsonPath("$.assignedCount").value(1));
    }

    @Test
    void assignShift_nonExistentEmployee_returns404WithEMPLOYEE_NOT_FOUND() throws Exception {
        Shift shift = shiftRepository.save(Shift.builder()
            .name("Ca Assign Ghost")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build());

        mockMvc.perform(put("/api/admin/shifts/" + shift.getId() + "/assign/nonexistent-emp-id")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("EMPLOYEE_NOT_FOUND"));
    }

    @Test
    void assignShift_adminRoleUser_returns400WithNOT_AN_EMPLOYEE() throws Exception {
        Shift shift = shiftRepository.save(Shift.builder()
            .name("Ca Assign Admin")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build());
        User anotherAdmin = userRepository.save(User.builder()
            .username("another_admin")
            .email("another_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin2"))
            .fullName("Another Admin")
            .role(UserRole.ADMIN)
            .build());

        mockMvc.perform(put("/api/admin/shifts/" + shift.getId() + "/assign/" + anotherAdmin.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("NOT_AN_EMPLOYEE"));
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
