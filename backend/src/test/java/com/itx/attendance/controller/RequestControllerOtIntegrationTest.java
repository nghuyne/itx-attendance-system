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

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 8 — Story 8.1: OT Pre-approval Request.
 *
 * Covers:
 *   AC-1 — POST /api/requests/ot creates a PENDING ot_request (happy path)
 *   AC-2 — plannedOtHours out of [0.5, 8.0] is rejected by DTO bean validation (VALIDATION_ERROR)
 *   AC-3 — plannedOtHours not a multiple of 0.5 → INVALID_OT_HOURS_GRANULARITY
 *   AC-4 — plannedDate in the past → INVALID_DATE
 *   AC-5 — reason shorter than 10 (trimmed) chars → INVALID_REASON_LENGTH
 *   AC-6 — a second PENDING request for the same employee+date → DUPLICATE_OT_REQUEST (409)
 *   AC-7 — approve/reject via the shared PUT /api/requests/{id}/approve|reject endpoints
 *   AC-8 — GET /api/requests/me surfaces OT requests with requestCategory="OT"
 */
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:sharedctxa;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "app.rate-limit.login.max-attempts=1000"
})
class RequestControllerOtIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private OtRequestRepository otRequestRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String employeeToken;
    private String leaderToken;
    private String otherLeaderToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        // OT request submission/approval triggers NotificationService, which writes rows
        // referencing the recipient user — clear those first so userRepository.deleteAll()
        // doesn't hit the FK.
        jdbcTemplate.execute("DELETE FROM notifications");
        otRequestRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(User.builder()
            .username("ot_admin")
            .email("ot_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("OT Admin")
            .role(UserRole.ADMIN)
            .build());

        User leader = userRepository.save(User.builder()
            .username("ot_leader")
            .email("ot_leader@itx.local")
            .passwordHash(passwordEncoder.encode("leader123"))
            .fullName("OT Leader")
            .role(UserRole.LEADER)
            .build());

        User otherLeader = userRepository.save(User.builder()
            .username("ot_other_leader")
            .email("ot_other_leader@itx.local")
            .passwordHash(passwordEncoder.encode("leader123"))
            .fullName("Other Leader")
            .role(UserRole.LEADER)
            .build());

        userRepository.save(User.builder()
            .username("ot_employee")
            .email("ot_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("OT Employee")
            .role(UserRole.EMPLOYEE)
            .leader(leader)
            .build());

        adminToken = loginAndGetToken("ot_admin", "admin123");
        leaderToken = loginAndGetToken("ot_leader", "leader123");
        otherLeaderToken = loginAndGetToken("ot_other_leader", "leader123");
        employeeToken = loginAndGetToken("ot_employee", "emp123");
    }

    // ── POST /api/requests/ot ────────────────────────────────────────────────

    @Test
    void submitOtRequest_valid_returns201WithPendingStatus() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "plannedDate", LocalDate.now().plusDays(1).toString(),
            "plannedOtHours", 2.5,
            "reason", "Hoàn thành báo cáo cuối tháng gấp"
        ));

        mockMvc.perform(post("/api/requests/ot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.plannedOtHours").value(2.5));
    }

    @Test
    void submitOtRequest_hoursBelowMin_returns400WithVALIDATION_ERROR() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "plannedDate", LocalDate.now().plusDays(1).toString(),
            "plannedOtHours", 0.2,
            "reason", "Số giờ dưới mức tối thiểu cho phép"
        ));

        mockMvc.perform(post("/api/requests/ot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void submitOtRequest_hoursAboveMax_returns400WithVALIDATION_ERROR() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "plannedDate", LocalDate.now().plusDays(1).toString(),
            "plannedOtHours", 8.5,
            "reason", "Số giờ vượt mức tối đa cho phép"
        ));

        mockMvc.perform(post("/api/requests/ot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void submitOtRequest_hoursNotMultipleOfHalf_returns400WithINVALID_OT_HOURS_GRANULARITY() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "plannedDate", LocalDate.now().plusDays(1).toString(),
            "plannedOtHours", 3.7,
            "reason", "Số giờ không phải bội số của 0.5"
        ));

        mockMvc.perform(post("/api/requests/ot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_OT_HOURS_GRANULARITY"));
    }

    @Test
    void submitOtRequest_pastDate_returns400WithINVALID_DATE() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "plannedDate", LocalDate.now().minusDays(1).toString(),
            "plannedOtHours", 2.0,
            "reason", "Ngày OT nằm trong quá khứ để test"
        ));

        mockMvc.perform(post("/api/requests/ot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_DATE"));
    }

    @Test
    void submitOtRequest_reasonTooShort_returns400WithINVALID_REASON_LENGTH() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "plannedDate", LocalDate.now().plusDays(1).toString(),
            "plannedOtHours", 2.0,
            "reason", "Quá ngắn"
        ));

        mockMvc.perform(post("/api/requests/ot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_REASON_LENGTH"));
    }

    @Test
    void submitOtRequest_duplicatePendingSameDate_returns409WithDUPLICATE_OT_REQUEST() throws Exception {
        String date = LocalDate.now().plusDays(2).toString();
        String firstBody = objectMapper.writeValueAsString(Map.of(
            "plannedDate", date,
            "plannedOtHours", 2.0,
            "reason", "Yêu cầu OT đầu tiên cho ngày này"
        ));
        mockMvc.perform(post("/api/requests/ot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstBody)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated());

        String secondBody = objectMapper.writeValueAsString(Map.of(
            "plannedDate", date,
            "plannedOtHours", 1.5,
            "reason", "Yêu cầu OT thứ hai trùng ngày với đơn đầu"
        ));
        mockMvc.perform(post("/api/requests/ot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondBody)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("DUPLICATE_OT_REQUEST"));
    }

    @Test
    void submitOtRequest_nonEmployeeRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "plannedDate", LocalDate.now().plusDays(1).toString(),
            "plannedOtHours", 2.0,
            "reason", "Admin không được tự gửi yêu cầu OT"
        ));

        mockMvc.perform(post("/api/requests/ot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden());
    }

    // ── PUT /api/requests/{id}/approve & /reject ─────────────────────────────

    @Test
    void approveOtRequest_byEmployeesLeader_returns200Approved() throws Exception {
        String requestId = createOtRequest(LocalDate.now().plusDays(1), 2.0, "OT phục vụ triển khai hệ thống");

        mockMvc.perform(put("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + leaderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.requestCategory").value("OT"));
    }

    @Test
    void approveOtRequest_byUnrelatedLeader_returns403() throws Exception {
        String requestId = createOtRequest(LocalDate.now().plusDays(1), 2.0, "OT không thuộc quyền duyệt của leader khác");

        mockMvc.perform(put("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + otherLeaderToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void approveOtRequest_alreadyApproved_returns400WithREQUEST_NOT_PENDING() throws Exception {
        String requestId = createOtRequest(LocalDate.now().plusDays(1), 2.0, "OT sẽ được duyệt hai lần liên tiếp");

        mockMvc.perform(put("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("REQUEST_NOT_PENDING"));
    }

    @Test
    void rejectOtRequest_byAdmin_setsRejectedWithReason() throws Exception {
        String requestId = createOtRequest(LocalDate.now().plusDays(1), 2.0, "OT sẽ bị từ chối trong bài test này");

        mockMvc.perform(put("/api/requests/" + requestId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Không đủ nhân sự cần thiết")))
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.reviewReason").value("Không đủ nhân sự cần thiết"));
    }

    // ── GET /api/requests/me ──────────────────────────────────────────────────

    @Test
    void getMyRequests_includesOtRequestWithCategoryOTAndPlannedFields() throws Exception {
        String date = LocalDate.now().plusDays(3).toString();
        createOtRequest(LocalDate.now().plusDays(3), 4.0, "OT xuất hiện trong danh sách yêu cầu của tôi");

        mockMvc.perform(get("/api/requests/me")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].requestCategory").value("OT"))
            .andExpect(jsonPath("$[0].plannedDate").value(date))
            .andExpect(jsonPath("$[0].plannedOtHours").value(4.0));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String createOtRequest(LocalDate plannedDate, double hours, String reason) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "plannedDate", plannedDate.toString(),
            "plannedOtHours", hours,
            "reason", reason
        ));
        String json = mockMvc.perform(post("/api/requests/ot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }
}
