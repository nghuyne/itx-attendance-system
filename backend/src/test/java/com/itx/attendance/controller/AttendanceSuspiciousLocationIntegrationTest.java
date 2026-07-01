package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.*;
import com.itx.attendance.dto.request.CheckInRequest;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Epic 8 — Story 8.2: Suspicious Location Admin Notification.
 *
 * A previous check-in is seeded directly via the repository (bypassing the "one check-in per
 * day" guard) so the test controls the distance/time delta precisely. The actual check-in under
 * test goes through the real POST /api/attendance/check-in endpoint in Client Site mode
 * (isClientSite=true) so GPS office-radius validation does not interfere with the suspicious
 * location calculation.
 *
 * The notification is created asynchronously on a dedicated taskExecutor (fire-and-forget after
 * the HTTP response), so assertions on notifications poll with a bounded timeout instead of
 * reading immediately after the check-in call returns.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:suspiciouslocationtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000",
    "app.ip-check.enabled=false",
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=minioadmin",
    "minio.secret-key=minioadmin",
    "minio.bucket-name=test-bucket"
})
class AttendanceSuspiciousLocationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String employeeToken;
    private User adminOne;
    private User adminTwo;
    private User employee;

    private static final String FAKE_PHOTO = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2Q==";

    // Previous check-in location (matches "office" area used across other integration tests)
    private static final double PREV_LAT = 10.78;
    private static final double PREV_LNG = 106.70;

    // ~1km from PREV_LAT/LNG — well under the 50km suspicious threshold
    private static final double NEARBY_LAT = 10.79;
    private static final double NEARBY_LNG = 106.70;

    // ~200km from PREV_LAT/LNG — well over the 50km suspicious threshold
    private static final double FAR_LAT = 12.65;
    private static final double FAR_LNG = 108.03;

    @BeforeEach
    void setUp() throws Exception {
        notificationRepository.deleteAll();
        attendanceRecordRepository.deleteAll();
        userRepository.deleteAll();
        shiftRepository.deleteAll();

        Shift shift = shiftRepository.save(Shift.builder()
            .name("Ca Sáng")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .checkInOpenMinutes(30)
            .lateInThreshold(15)
            .earlyOutThreshold(15)
            .halfDayThreshold(240)
            .otBuffer(30)
            .build());

        adminOne = userRepository.save(User.builder()
            .username("susp_admin1")
            .email("susp_admin1@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Suspicious Admin One")
            .role(UserRole.ADMIN)
            .build());

        adminTwo = userRepository.save(User.builder()
            .username("susp_admin2")
            .email("susp_admin2@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Suspicious Admin Two")
            .role(UserRole.ADMIN)
            .build());

        employee = userRepository.save(User.builder()
            .username("susp_employee")
            .email("susp_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Suspicious Employee")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build());

        employeeToken = loginAndGetToken("susp_employee", "emp123");
    }

    @Test
    void checkIn_farFromRecentPreviousCheckIn_flagsSuspiciousAndNotifiesAllAdmins() throws Exception {
        seedPreviousCheckIn(PREV_LAT, PREV_LNG, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(20));

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(FAR_LAT, FAR_LNG, FAKE_PHOTO, true, null));

        String json = mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.suspiciousLocation").value(true))
            .andReturn().getResponse().getContentAsString();
        String recordId = objectMapper.readTree(json).get("id").asText();

        awaitNotification(adminOne.getId(), recordId);
        awaitNotification(adminTwo.getId(), recordId);

        Notification notifForAdminOne = notificationRepository.findByRecipientIdAndIsReadFalse(adminOne.getId())
            .stream().findFirst().orElseThrow();
        assertThat(notifForAdminOne.getType()).isEqualTo(NotificationType.SUSPICIOUS_LOCATION);
        assertThat(notifForAdminOne.getMessage()).contains("Suspicious Employee");
        assertThat(notifForAdminOne.getMessage()).contains("vị trí bất thường");
    }

    @Test
    void checkIn_nearRecentPreviousCheckIn_doesNotFlagSuspicious() throws Exception {
        seedPreviousCheckIn(PREV_LAT, PREV_LNG, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(20));

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(NEARBY_LAT, NEARBY_LNG, FAKE_PHOTO, true, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.suspiciousLocation").value(false));

        // No async notification path is even triggered when suspiciousLocation is false —
        // give the executor a brief grace window, then assert no notification was written.
        Thread.sleep(300);
        assertThat(notificationRepository.findByRecipientIdAndIsReadFalse(adminOne.getId())).isEmpty();
    }

    @Test
    void checkIn_previousCheckInOlderThan24Hours_doesNotFlagSuspicious() throws Exception {
        seedPreviousCheckIn(PREV_LAT, PREV_LNG, LocalDateTime.now(ZoneOffset.UTC).minusHours(25));

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(FAR_LAT, FAR_LNG, FAKE_PHOTO, true, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.suspiciousLocation").value(false));
    }

    @Test
    void checkIn_noPreviousCheckIn_doesNotFlagSuspicious() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(FAR_LAT, FAR_LNG, FAKE_PHOTO, true, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.suspiciousLocation").value(false));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void seedPreviousCheckIn(double lat, double lng, LocalDateTime checkInUtc) {
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee)
            .shift(employee.getShift())
            .date(LocalDate.now(ZoneOffset.UTC).minusDays(1))
            .checkInTime(checkInUtc)
            .checkInIp("127.0.0.1")
            .checkInLat(BigDecimal.valueOf(lat))
            .checkInLng(BigDecimal.valueOf(lng))
            .checkInPhotoUrl("photo.jpg")
            .checkOutTime(checkInUtc.plusHours(8))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());
    }

    private void awaitNotification(String recipientId, String referenceId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (notificationRepository.existsByRecipientIdAndTypeAndReferenceId(
                    recipientId, NotificationType.SUSPICIOUS_LOCATION, referenceId)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Expected SUSPICIOUS_LOCATION notification for recipient="
            + recipientId + ", referenceId=" + referenceId + " was not created within 5s");
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
