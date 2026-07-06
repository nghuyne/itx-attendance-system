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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 6 — Story 6.1: Leave Request & Leave Balance (GAP-01).
 *
 * Covers:
 *   AC-1 — POST /api/requests/leave creates a request, computes totalDays as business days
 *   AC-2 — INVALID_DATE_RANGE for past start dates and start-after-end ranges
 *   AC-3 — INSUFFICIENT_LEAVE_BALANCE when remaining days < requested total
 *   AC-4 — LEAVE_DATE_CONFLICT when dates overlap an existing PENDING/APPROVED leave
 *   AC-5 — GET /api/requests/leave-balance seeds default ANNUAL=12 / SICK=5
 *   AC-6 — approve deducts used_days; reject leaves balance untouched and records reason
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:leaverequesttestdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
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
class LeaveRequestControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private LeaveBalanceRepository leaveBalanceRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String employeeToken;
    private User employee;

    @BeforeEach
    void setUp() throws Exception {
        // Leave requests trigger NotificationService, which writes rows referencing the
        // recipient user — clear those first so userRepository.deleteAll() doesn't hit the FK.
        jdbcTemplate.execute("DELETE FROM notifications");
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        userRepository.deleteAll();

        User adminUser = userRepository.save(User.builder()
            .username("lv_admin")
            .email("lv_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Leave Admin")
            .role(UserRole.ADMIN)
            .build());

        employee = userRepository.save(User.builder()
            .username("lv_employee")
            .email("lv_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Leave Employee")
            .role(UserRole.EMPLOYEE)
            .build());

        adminToken = loginAndGetToken("lv_admin", "admin123");
        employeeToken = loginAndGetToken("lv_employee", "emp123");
    }

    // ── POST /api/requests/leave ─────────────────────────────────────────────

    @Test
    void submitLeaveRequest_validRange_returns201WithComputedTotalDays() throws Exception {
        LocalDate start = nextBusinessDay(LocalDate.now());
        LocalDate end = addBusinessDays(start, 2); // 3 business days total

        String body = objectMapper.writeValueAsString(Map.of(
            "leaveType", "ANNUAL",
            "startDate", start.toString(),
            "endDate", end.toString(),
            "reason", "Nghỉ phép thăm gia đình ở quê"
        ));

        mockMvc.perform(post("/api/requests/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.leaveType").value("ANNUAL"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.totalDays").value(3));
    }

    @Test
    void submitLeaveRequest_startDateInPast_returns400WithINVALID_DATE_RANGE() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "leaveType", "ANNUAL",
            "startDate", LocalDate.now().minusDays(1).toString(),
            "endDate", LocalDate.now().plusDays(1).toString(),
            "reason", "Ngày bắt đầu trong quá khứ để test validation"
        ));

        mockMvc.perform(post("/api/requests/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_DATE_RANGE"));
    }

    @Test
    void submitLeaveRequest_startAfterEnd_returns400WithINVALID_DATE_RANGE() throws Exception {
        LocalDate start = nextBusinessDay(LocalDate.now()).plusDays(5);
        LocalDate end = start.minusDays(1);

        String body = objectMapper.writeValueAsString(Map.of(
            "leaveType", "ANNUAL",
            "startDate", start.toString(),
            "endDate", end.toString(),
            "reason", "Ngày kết thúc trước ngày bắt đầu để test"
        ));

        mockMvc.perform(post("/api/requests/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_DATE_RANGE"));
    }

    @Test
    void submitLeaveRequest_exceedsRemainingBalance_returns400WithINSUFFICIENT_LEAVE_BALANCE() throws Exception {
        LocalDate start = nextBusinessDay(LocalDate.now());
        LocalDate end = addBusinessDays(start, 12); // 13 business days > default ANNUAL=12

        String body = objectMapper.writeValueAsString(Map.of(
            "leaveType", "ANNUAL",
            "startDate", start.toString(),
            "endDate", end.toString(),
            "reason", "Xin nghỉ dài ngày vượt quá quỹ phép còn lại"
        ));

        mockMvc.perform(post("/api/requests/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INSUFFICIENT_LEAVE_BALANCE"));
    }

    @Test
    void submitLeaveRequest_overlapsExistingPendingRequest_returns409WithLEAVE_DATE_CONFLICT() throws Exception {
        LocalDate start = nextBusinessDay(LocalDate.now());
        LocalDate end = addBusinessDays(start, 1);

        String firstBody = objectMapper.writeValueAsString(Map.of(
            "leaveType", "ANNUAL",
            "startDate", start.toString(),
            "endDate", end.toString(),
            "reason", "Đơn nghỉ phép đầu tiên hợp lệ"
        ));
        mockMvc.perform(post("/api/requests/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstBody)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated());

        String overlappingBody = objectMapper.writeValueAsString(Map.of(
            "leaveType", "SICK",
            "startDate", start.toString(),
            "endDate", end.toString(),
            "reason", "Đơn nghỉ phép trùng ngày với đơn đầu tiên"
        ));
        mockMvc.perform(post("/api/requests/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(overlappingBody)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("LEAVE_DATE_CONFLICT"));
    }

    @Test
    void submitLeaveRequest_nonEmployeeRole_returns403() throws Exception {
        LocalDate start = nextBusinessDay(LocalDate.now());
        LocalDate end = addBusinessDays(start, 1);

        String body = objectMapper.writeValueAsString(Map.of(
            "leaveType", "ANNUAL",
            "startDate", start.toString(),
            "endDate", end.toString(),
            "reason", "Admin không được tự gửi đơn nghỉ phép"
        ));

        mockMvc.perform(post("/api/requests/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden());
    }

    // ── GET /api/requests/leave-balance ──────────────────────────────────────

    @Test
    void getLeaveBalance_noExistingBalance_seedsDefaultAnnual12AndSick5() throws Exception {
        String json = mockMvc.perform(get("/api/requests/leave-balance")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode balances = objectMapper.readTree(json);
        assertThat(balances).hasSizeGreaterThanOrEqualTo(2);

        int annualTotal = -1;
        int sickTotal = -1;
        for (com.fasterxml.jackson.databind.JsonNode b : balances) {
            if (b.get("leaveType").asText().equals("ANNUAL")) annualTotal = b.get("totalDays").asInt();
            if (b.get("leaveType").asText().equals("SICK")) sickTotal = b.get("totalDays").asInt();
        }
        assertThat(annualTotal).isEqualTo(12);
        assertThat(sickTotal).isEqualTo(5);
    }

    // ── Approve / Reject balance side-effects ────────────────────────────────

    @Test
    void approveLeaveRequest_increasesUsedDaysByTotalDays() throws Exception {
        LocalDate start = nextBusinessDay(LocalDate.now());
        LocalDate end = addBusinessDays(start, 2); // 3 business days

        String body = objectMapper.writeValueAsString(Map.of(
            "leaveType", "ANNUAL",
            "startDate", start.toString(),
            "endDate", end.toString(),
            "reason", "Đơn nghỉ phép sẽ được duyệt trong test"
        ));
        String createdJson = mockMvc.perform(post("/api/requests/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(createdJson).get("id").asText();

        mockMvc.perform(put("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        LeaveBalance balance = leaveBalanceRepository
            .findByEmployeeIdAndYearAndLeaveType(employee.getId(), start.getYear(), LeaveType.ANNUAL)
            .orElseThrow();
        assertThat(balance.getUsedDays()).isEqualTo(3);
    }

    @Test
    void rejectLeaveRequest_setsReasonAndDoesNotChangeUsedDays() throws Exception {
        LocalDate start = nextBusinessDay(LocalDate.now());
        LocalDate end = addBusinessDays(start, 1); // 2 business days

        String body = objectMapper.writeValueAsString(Map.of(
            "leaveType", "SICK",
            "startDate", start.toString(),
            "endDate", end.toString(),
            "reason", "Đơn nghỉ phép sẽ bị từ chối trong test"
        ));
        String createdJson = mockMvc.perform(post("/api/requests/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(createdJson).get("id").asText();

        mockMvc.perform(put("/api/requests/" + requestId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Không đủ lý do chính đáng để nghỉ phép")))
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));

        LeaveBalance balance = leaveBalanceRepository
            .findByEmployeeIdAndYearAndLeaveType(employee.getId(), start.getYear(), LeaveType.SICK)
            .orElseThrow();
        assertThat(balance.getUsedDays()).isEqualTo(0);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private LocalDate nextBusinessDay(LocalDate from) {
        return addBusinessDays(from, 1);
    }

    private LocalDate addBusinessDays(LocalDate from, int businessDaysToAdd) {
        LocalDate date = from;
        int added = 0;
        while (added < businessDaysToAdd) {
            date = date.plusDays(1);
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return date;
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
