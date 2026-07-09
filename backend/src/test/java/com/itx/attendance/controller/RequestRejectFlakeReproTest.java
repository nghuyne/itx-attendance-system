package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.LeaveRequestRepository;
import com.itx.attendance.repository.OtRequestRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Diagnostic-only test (Hibernate flakiness investigation, backlog item 4:
 * see _bmad-output/implementation-artifacts/investigations/hibernate-loadcontexts-flakiness-investigation.md).
 *
 * Repeatedly calls PUT /api/requests/{id}/reject for a fresh OT request and a fresh Leave
 * request back-to-back, 40 times, in complete isolation from the rest of the suite (own H2
 * database, no other test class runs concurrently). Reject was chosen because it is the
 * deepest dispatch chain (Leave = 4 sequential PESSIMISTIC_WRITE probe queries on one
 * session, per RequestService.rejectRequest/253-302) and the two paths reported flaky in
 * the prior full-suite runs (rejectOtRequest, rejectLeaveRequest) are exactly the two
 * deepest branches.
 *
 * Goal: determine whether the chained pessimistic-lock probing is *sufficient on its own*
 * to reproduce "Illegal pop() with non-matching JdbcValuesSourceProcessingState" (Hypothesis 2),
 * independent of full-suite load / virtual threads (Hypothesis 1, tested separately by running
 * the full suite with -Dspring.threads.virtual.enabled=false).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:rejectflakereprodb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
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
class RequestRejectFlakeReproTest {

    private static final int ITERATIONS = 40;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private OtRequestRepository otRequestRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String employeeToken;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("DELETE FROM notifications");
        otRequestRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
            .username("repro_admin")
            .email("repro_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Repro Admin")
            .role(UserRole.ADMIN)
            .build());

        userRepository.save(User.builder()
            .username("repro_employee")
            .email("repro_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Repro Employee")
            .role(UserRole.EMPLOYEE)
            .build());

        adminToken = loginAndGetToken("repro_admin", "admin123");
        employeeToken = loginAndGetToken("repro_employee", "emp123");
    }

    @Test
    void repeatedOtAndLeaveRejectBackToBack_doesNotThrowIllegalPop() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            String otId = createOtRequest(LocalDate.now().plusDays(2 + i), 2.0,
                "Lap lai reject de tai hien loi Hibernate lan " + i);
            rejectRequest(otId, "Tu choi lap " + i);

            LocalDate leaveStart = addBusinessDays(LocalDate.now(), 50 + i * 5);
            LocalDate leaveEnd = addBusinessDays(leaveStart, 1);
            String leaveId = createLeaveRequest(leaveStart, leaveEnd,
                "Lap lai reject de tai hien loi Hibernate lan " + i);
            rejectRequest(leaveId, "Tu choi lap " + i);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    private String createLeaveRequest(LocalDate start, LocalDate end, String reason) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "leaveType", "ANNUAL",
            "startDate", start.toString(),
            "endDate", end.toString(),
            "reason", reason
        ));
        String json = mockMvc.perform(post("/api/requests/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }

    private void rejectRequest(String requestId, String reason) throws Exception {
        mockMvc.perform(put("/api/requests/" + requestId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason)))
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
