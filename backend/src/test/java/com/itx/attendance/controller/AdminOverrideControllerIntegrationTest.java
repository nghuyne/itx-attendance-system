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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 5 — Story 5.1: Admin Manual Override of Attendance Records
 *
 * Covers:
 *   AC-1 — audit_logs table / immutable AuditLog written for every changed field
 *   AC-2 — PUT /api/admin/attendance/{id}/override validation + recalculation
 *   AC-3 — Overriding an ABSENT record with both times recalculates a real status
 *   AC-4 — Non-ADMIN callers get 403 {"error":"FORBIDDEN"}
 *   AC-5 — GET /api/admin/attendance search + filters
 */
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:sharedctxa;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "app.rate-limit.login.max-attempts=1000"
})
class AdminOverrideControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String employeeToken;
    private User adminUser;
    private User employee;
    private Shift shift;

    @BeforeEach
    void setUp() throws Exception {
        // AuditLogRepository.deleteAll() is intentionally unsupported (audit logs are immutable);
        // truncate via raw SQL so admin_id FK doesn't block user cleanup between tests.
        jdbcTemplate.execute("DELETE FROM audit_logs");
        attendanceRecordRepository.deleteAll();
        userRepository.deleteAll();
        shiftRepository.deleteAll();

        shift = shiftRepository.save(Shift.builder()
            .name("Ca Sáng")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .checkInOpenMinutes(30)
            .lateInThreshold(15)
            .earlyOutThreshold(15)
            .halfDayThreshold(240)
            .otBuffer(30)
            .build());

        adminUser = userRepository.save(User.builder()
            .username("ov_admin")
            .email("ov_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Override Admin")
            .role(UserRole.ADMIN)
            .build());

        employee = userRepository.save(User.builder()
            .username("ov_employee")
            .email("ov_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Override Employee")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build());

        adminToken = loginAndGetToken("ov_admin", "admin123");
        employeeToken = loginAndGetToken("ov_employee", "emp123");
    }

    // ── PUT /api/admin/attendance/{id}/override ─────────────────────────────

    @Test
    void override_validTimesBothProvided_recalculatesStatusAndWritesAuditLogs() throws Exception {
        AttendanceRecord record = attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee)
            .shift(shift)
            .date(LocalDate.of(2026, 6, 15))
            .checkInTime(LocalDateTime.of(2026, 6, 15, 1, 0))   // 08:00 VN
            .checkOutTime(LocalDateTime.of(2026, 6, 15, 10, 0)) // 17:00 VN
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());

        String body = objectMapper.writeValueAsString(Map.of(
            "checkInTime", "2026-06-15T08:30:00",  // 08:30 VN local -> stored as-is
            "auditReason", "Nhân viên quên chấm công đúng giờ, xác nhận qua camera"
        ));

        mockMvc.perform(put("/api/admin/attendance/" + record.getId() + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAdminOverride").value(true))
            .andExpect(jsonPath("$.approvalSubStatus").value("ADMIN_OVERRIDE"));

        assertThat(auditLogRepository.findAll()).isNotEmpty();
        assertThat(auditLogRepository.findAll())
            .anyMatch(log -> log.getFieldChanged().equals("check_in_time")
                && log.getReason().equals("Nhân viên quên chấm công đúng giờ, xác nhận qua camera"));
    }

    @Test
    void override_blankAuditReason_returns400() throws Exception {
        AttendanceRecord record = createRecord();

        String body = objectMapper.writeValueAsString(Map.of(
            "checkInTime", "2026-06-15T08:00:00",
            "auditReason", ""
        ));

        mockMvc.perform(put("/api/admin/attendance/" + record.getId() + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void override_auditReasonTooShort_returns400() throws Exception {
        AttendanceRecord record = createRecord();

        String body = objectMapper.writeValueAsString(Map.of(
            "checkInTime", "2026-06-15T08:00:00",
            "auditReason", "quá ngắn"
        ));

        mockMvc.perform(put("/api/admin/attendance/" + record.getId() + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void override_nonAdmin_returns403WithForbiddenBody() throws Exception {
        AttendanceRecord record = createRecord();

        String body = objectMapper.writeValueAsString(Map.of(
            "checkInTime", "2026-06-15T08:00:00",
            "auditReason", "Sửa lại giờ vào cho đúng thực tế"
        ));

        mockMvc.perform(put("/api/admin/attendance/" + record.getId() + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void override_recordNotFound_returns404WithRECORD_NOT_FOUND() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "checkInTime", "2026-06-15T08:00:00",
            "auditReason", "Sửa lại giờ vào cho đúng thực tế"
        ));

        mockMvc.perform(put("/api/admin/attendance/nonexistent-id/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("RECORD_NOT_FOUND"));
    }

    @Test
    void override_statusAndTimeBothProvided_returns400WithAMBIGUOUS_OVERRIDE() throws Exception {
        AttendanceRecord record = createRecord();

        String body = objectMapper.writeValueAsString(Map.of(
            "checkInTime", "2026-06-15T08:00:00",
            "attendanceStatus", "ON_TIME",
            "auditReason", "Sửa lại giờ vào cho đúng thực tế"
        ));

        mockMvc.perform(put("/api/admin/attendance/" + record.getId() + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("AMBIGUOUS_OVERRIDE"));
    }

    @Test
    void override_checkOutBeforeCheckIn_returns400WithINVALID_TIME_RANGE() throws Exception {
        AttendanceRecord record = createRecord();

        String body = objectMapper.writeValueAsString(Map.of(
            "checkInTime", "2026-06-15T17:00:00",
            "checkOutTime", "2026-06-15T08:00:00",
            "auditReason", "Thử nghiệm giờ ra trước giờ vào"
        ));

        mockMvc.perform(put("/api/admin/attendance/" + record.getId() + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_TIME_RANGE"));
    }

    @Test
    void override_absentRecordWithBothTimes_recalculatesToComputedStatus() throws Exception {
        AttendanceRecord record = attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee)
            .shift(shift)
            .date(LocalDate.of(2026, 6, 16))
            .attendanceStatus(AttendanceStatus.ABSENT)
            .build());

        String body = objectMapper.writeValueAsString(Map.of(
            "checkInTime", "2026-06-16T01:00:00",  // 08:00 VN
            "checkOutTime", "2026-06-16T10:00:00", // 17:00 VN
            "auditReason", "Nhân viên có mặt thực tế, admin bổ sung dữ liệu chấm công"
        ));

        mockMvc.perform(put("/api/admin/attendance/" + record.getId() + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attendanceStatus").value("ON_TIME"))
            .andExpect(jsonPath("$.isAdminOverride").value(true));
    }

    @Test
    void override_onlyCheckInTimeNoCheckout_statusIsNotOverwrittenToIncomplete() throws Exception {
        AttendanceRecord record = attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee)
            .shift(shift)
            .date(LocalDate.of(2026, 6, 17))
            .attendanceStatus(AttendanceStatus.ABSENT)
            .build());

        String body = objectMapper.writeValueAsString(Map.of(
            "checkInTime", "2026-06-17T01:00:00",
            "auditReason", "Chỉ bổ sung giờ vào, chưa có giờ ra"
        ));

        mockMvc.perform(put("/api/admin/attendance/" + record.getId() + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attendanceStatus").value("ABSENT"))
            .andExpect(jsonPath("$.isAdminOverride").value(true));
    }

    @Test
    void override_explicitStatusOnly_updatesStatusDirectlyWithoutTimeChange() throws Exception {
        AttendanceRecord record = createRecord();

        String body = objectMapper.writeValueAsString(Map.of(
            "attendanceStatus", "EXCUSED",
            "auditReason", "Nhân viên nghỉ có phép, admin cập nhật trạng thái"
        ));

        mockMvc.perform(put("/api/admin/attendance/" + record.getId() + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attendanceStatus").value("EXCUSED"));
    }

    @Test
    void override_photoUrlOnly_updatesCheckInPhotoUrlAndWritesAuditLog() throws Exception {
        AttendanceRecord record = createRecord();

        String body = objectMapper.writeValueAsString(Map.of(
            "photoUrl", "https://cdn.itx.local/photos/new.jpg",
            "auditReason", "Ảnh gốc bị lỗi, admin thay ảnh minh chứng khác"
        ));

        mockMvc.perform(put("/api/admin/attendance/" + record.getId() + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkInPhotoUrl").value("https://cdn.itx.local/photos/new.jpg"));

        assertThat(auditLogRepository.findAll())
            .anyMatch(log -> log.getFieldChanged().equals("check_in_photo_url"));
    }

    // ── GET /api/admin/attendance ─────────────────────────────────────────────

    @Test
    void searchAttendance_returnsPaginatedRecordsWithinRange() throws Exception {
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift)
            .date(LocalDate.of(2026, 6, 15))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift)
            .date(LocalDate.of(2026, 6, 16))
            .attendanceStatus(AttendanceStatus.LATE_IN)
            .build());

        mockMvc.perform(get("/api/admin/attendance")
                .param("from", "2026-06-15")
                .param("to", "2026-06-16")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void searchAttendance_filterByEmployeeId_returnsOnlyMatchingRecords() throws Exception {
        User otherEmployee = userRepository.save(User.builder()
            .username("ov_employee2")
            .email("ov_employee2@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Other Employee")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build());

        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift)
            .date(LocalDate.of(2026, 6, 15))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(otherEmployee).shift(shift)
            .date(LocalDate.of(2026, 6, 15))
            .attendanceStatus(AttendanceStatus.LATE_IN)
            .build());

        mockMvc.perform(get("/api/admin/attendance")
                .param("from", "2026-06-15")
                .param("to", "2026-06-15")
                .param("employeeId", employee.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].employeeId").value(employee.getId()));
    }

    @Test
    void searchAttendance_filterByStatus_returnsOnlyMatchingStatus() throws Exception {
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift)
            .date(LocalDate.of(2026, 6, 15))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift)
            .date(LocalDate.of(2026, 6, 16))
            .attendanceStatus(AttendanceStatus.LATE_IN)
            .build());

        mockMvc.perform(get("/api/admin/attendance")
                .param("from", "2026-06-15")
                .param("to", "2026-06-16")
                .param("status", "LATE_IN")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].attendanceStatus").value("LATE_IN"));
    }

    @Test
    void searchAttendance_multipleStatuses_returnsRecordsMatchingEitherStatus() throws Exception {
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift)
            .date(LocalDate.of(2026, 6, 15))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift)
            .date(LocalDate.of(2026, 6, 16))
            .attendanceStatus(AttendanceStatus.LATE_IN)
            .build());
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift)
            .date(LocalDate.of(2026, 6, 17))
            .attendanceStatus(AttendanceStatus.ABSENT)
            .build());

        mockMvc.perform(get("/api/admin/attendance")
                .param("from", "2026-06-15")
                .param("to", "2026-06-17")
                .param("status", "LATE_IN", "ABSENT")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[*].attendanceStatus", containsInAnyOrder("LATE_IN", "ABSENT")));
    }

    @Test
    void searchAttendance_noStatusParam_returnsAllStatuses() throws Exception {
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift)
            .date(LocalDate.of(2026, 6, 15))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift)
            .date(LocalDate.of(2026, 6, 16))
            .attendanceStatus(AttendanceStatus.LATE_IN)
            .build());

        mockMvc.perform(get("/api/admin/attendance")
                .param("from", "2026-06-15")
                .param("to", "2026-06-16")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void searchAttendance_invalidStatusValue_returns400WithINVALID_STATUS() throws Exception {
        mockMvc.perform(get("/api/admin/attendance")
                .param("from", "2026-06-15")
                .param("to", "2026-06-16")
                .param("status", "NOT_A_REAL_STATUS")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_STATUS"));
    }

    @Test
    void searchAttendance_fromAfterTo_returns400WithINVALID_DATE_RANGE() throws Exception {
        mockMvc.perform(get("/api/admin/attendance")
                .param("from", "2026-06-16")
                .param("to", "2026-06-15")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_DATE_RANGE"));
    }

    @Test
    void searchAttendance_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/attendance")
                .param("from", "2026-06-15")
                .param("to", "2026-06-16")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AttendanceRecord createRecord() {
        return attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee)
            .shift(shift)
            .date(LocalDate.of(2026, 6, 15))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());
    }
}
