package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.*;
import com.itx.attendance.dto.request.CheckInRequest;
import com.itx.attendance.dto.request.CheckOutRequest;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 3 — Employee Check-in/Out & Automated Status Classification
 *
 * Stories covered:
 *   3.1 — Core Check-in API (Office Mode): IP validation bypass, shift guard, duplicate guard
 *   3.3 — Client Site Mode: GPS required guard
 *   3.4 — Check-out & Status Classification: NO_CHECKIN_FOUND, history pagination, invalid date range
 *   3.5 — GET /api/attendance/today: record found vs. no record
 *
 * No @MockBean used — PhotoService.decodeBase64Photo works with a tiny base64 payload and
 * uploadPhotoAsync fails silently (exception is caught in .exceptionally()). OfficeLocationService
 * returns early from validateRadius() when no active office locations exist in the DB.
 * IP check is disabled via app.ip-check.enabled=false.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:attendanceintegtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
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
class AttendanceControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String employeeToken;
    private User employee;
    private Shift shift;

    // Tiny valid base64 payload: decodeBase64Photo strips prefix, decodes 22 bytes (< 512 KB).
    // uploadPhotoAsync connects to MinIO and fails, but .exceptionally() swallows the error.
    private static final String FAKE_PHOTO = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2Q==";

    @BeforeEach
    void setUp() throws Exception {
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

        employee = userRepository.save(User.builder()
            .username("att_employee")
            .email("att_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Attendance Employee")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build());

        employeeToken = loginAndGetToken("att_employee", "emp123");
    }

    // ── POST /api/attendance/check-in ───────────────────────────────────────

    @Test
    void checkIn_validPayload_returns201WithRecord() throws Exception {
        // lat/lng null → skips OfficeLocationService.validateRadius() call
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.attendanceStatus").isNotEmpty())
            .andExpect(jsonPath("$.isClientSite").value(false))
            .andExpect(jsonPath("$.gpsUnavailable").value(true));
    }

    @Test
    void checkIn_withGps_setsGpsUnavailableFalse() throws Exception {
        // GPS provided + no active office locations → validateRadius returns early (passes)
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(10.77, 106.69, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.gpsUnavailable").value(false));
    }

    @Test
    void checkIn_noShiftAssigned_returns400WithNO_SHIFT_ASSIGNED() throws Exception {
        User noShiftEmp = userRepository.save(User.builder()
            .username("no_shift_emp")
            .email("no_shift@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("No Shift Employee")
            .role(UserRole.EMPLOYEE)
            .build());
        String noShiftToken = loginAndGetToken("no_shift_emp", "emp123");

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + noShiftToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("NO_SHIFT_ASSIGNED"));
    }

    @Test
    void checkIn_duplicateOnSameDay_returns409WithALREADY_CHECKED_IN() throws Exception {
        LocalDate today = LocalDate.now();
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee)
            .shift(shift)
            .date(today)
            .checkInTime(LocalDateTime.now().minusHours(2))
            .checkInIp("127.0.0.1")
            .checkInPhotoUrl("emp-id/photo.jpg")
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("ALREADY_CHECKED_IN"));
    }

    @Test
    void checkIn_missingPhotoBase64_returns400() throws Exception {
        String body = "{\"lat\":null,\"lng\":null,\"isClientSite\":false}";

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void checkIn_unauthenticated_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    // ── POST /api/attendance/check-in — Story 3.3: Client Site Mode ─────────

    @Test
    void checkIn_clientSiteMode_withGps_returns201AndSetsClientSiteTrue() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(10.77, 106.69, FAKE_PHOTO, true, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isClientSite").value(true));
    }

    @Test
    void checkIn_clientSiteMode_missingGps_returns400WithGPS_REQUIRED() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, true, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("GPS_REQUIRED"));
    }

    @Test
    void checkIn_clientSiteMode_withLatOnlyNoLng_returns400WithGPS_REQUIRED() throws Exception {
        String body = "{\"lat\":10.77,\"lng\":null,\"photoBase64\":\"" + FAKE_PHOTO + "\",\"isClientSite\":true}";

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("GPS_REQUIRED"));
    }

    // ── GET /api/attendance/today ───────────────────────────────────────────

    @Test
    void getToday_noRecordForToday_returns204() throws Exception {
        mockMvc.perform(get("/api/attendance/today")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void getToday_recordExists_returns200WithRecord() throws Exception {
        LocalDate today = LocalDate.now();
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee)
            .shift(shift)
            .date(today)
            .checkInTime(LocalDateTime.now().minusHours(2))
            .checkInIp("127.0.0.1")
            .checkInPhotoUrl("emp-id/photo.jpg")
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());

        mockMvc.perform(get("/api/attendance/today")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attendanceStatus").value("ON_TIME"))
            .andExpect(jsonPath("$.date").value(today.toString()));
    }

    // ── POST /api/attendance/check-out — Story 3.4 ─────────────────────────

    @Test
    void checkOut_noPriorCheckIn_returns400WithNO_CHECKIN_FOUND() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckOutRequest(null, null, FAKE_PHOTO));

        mockMvc.perform(post("/api/attendance/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("NO_CHECKIN_FOUND"));
    }

    @Test
    void checkOut_validCheckIn_returns200WithCheckOutTime() throws Exception {
        LocalDate today = LocalDate.now();
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee)
            .shift(shift)
            .date(today)
            .checkInTime(LocalDateTime.now().minusHours(8))
            .checkInIp("127.0.0.1")
            .checkInPhotoUrl("emp-id/photo.jpg")
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());

        String body = objectMapper.writeValueAsString(
            new CheckOutRequest(null, null, FAKE_PHOTO));

        mockMvc.perform(post("/api/attendance/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkOutTime").isNotEmpty())
            .andExpect(jsonPath("$.attendanceStatus").isNotEmpty());
    }

    @Test
    void checkOut_whenAlreadyCheckedOut_returns400WithNO_CHECKIN_FOUND() throws Exception {
        // Service queries findFirstBy...CheckOutTimeIsNull..., so an already-checked-out record
        // is never found → returns NO_CHECKIN_FOUND (400) rather than ALREADY_CHECKED_OUT (409).
        LocalDate today = LocalDate.now();
        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee)
            .shift(shift)
            .date(today)
            .checkInTime(LocalDateTime.now().minusHours(8))
            .checkOutTime(LocalDateTime.now().minusHours(1))
            .checkInIp("127.0.0.1")
            .checkInPhotoUrl("emp-id/photo.jpg")
            .checkOutPhotoUrl("emp-id/checkout.jpg")
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());

        String body = objectMapper.writeValueAsString(
            new CheckOutRequest(null, null, FAKE_PHOTO));

        mockMvc.perform(post("/api/attendance/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("NO_CHECKIN_FOUND"));
    }

    @Test
    void checkOut_missingPhoto_returns400() throws Exception {
        String body = "{\"lat\":null,\"lng\":null}";

        mockMvc.perform(post("/api/attendance/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest());
    }

    // ── GET /api/attendance/history ─────────────────────────────────────────

    @Test
    void getHistory_validDateRange_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/attendance/history")
                .param("from", "2026-06-01")
                .param("to", "2026-06-30")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getHistory_withRecords_returnsPaginatedList() throws Exception {
        LocalDate day1 = LocalDate.of(2026, 6, 30);
        LocalDate day2 = LocalDate.of(2026, 6, 29);

        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift).date(day1)
            .checkInTime(day1.atTime(8, 0))
            .checkInIp("127.0.0.1").checkInPhotoUrl("photo1.jpg")
            .attendanceStatus(AttendanceStatus.ON_TIME).build());

        attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee).shift(shift).date(day2)
            .checkInTime(day2.atTime(8, 20))
            .checkInIp("127.0.0.1").checkInPhotoUrl("photo2.jpg")
            .attendanceStatus(AttendanceStatus.LATE_IN).build());

        mockMvc.perform(get("/api/attendance/history")
                .param("from", "2026-06-01")
                .param("to", "2026-06-30")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].attendanceStatus",
                hasItems("ON_TIME", "LATE_IN")));
    }

    @Test
    void getHistory_fromAfterTo_returns400WithINVALID_DATE() throws Exception {
        mockMvc.perform(get("/api/attendance/history")
                .param("from", "2026-06-30")
                .param("to", "2026-06-01")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_DATE"));
    }

    @Test
    void getHistory_paginated_respectsPageAndSize() throws Exception {
        mockMvc.perform(get("/api/attendance/history")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .param("page", "0")
                .param("size", "5")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(5))
            .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void getHistory_missingFromParam_returns400() throws Exception {
        mockMvc.perform(get("/api/attendance/history")
                .param("to", "2026-06-30")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/attendance/history")
                .param("from", "2026-06-01")
                .param("to", "2026-06-30"))
            .andExpect(status().isUnauthorized());
    }

    // ── Role-based access: ADMIN cannot use employee endpoints ──────────────

    @Test
    void checkIn_adminRole_returns403() throws Exception {
        userRepository.save(User.builder()
            .username("att_admin")
            .email("att_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Att Admin")
            .role(UserRole.ADMIN)
            .build());
        String adminToken = loginAndGetToken("att_admin", "admin123");

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden());
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
