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
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 10 — Story 10.2: Hybrid MAC + IP check-in validation.
 *
 * Backend distinguishes mobile (Android/Capacitor) vs web check-in requests by the
 * presence of the `bssid` field: non-null/non-blank → validate against valid_macs,
 * null/blank → fall back to the existing IP validation (valid_ips).
 *
 * app.ip-check.enabled=true here (opposite of AttendanceControllerIntegrationTest,
 * which disables it) so both validation branches actually execute.
 */
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:attendancehybridtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "app.rate-limit.login.max-attempts=1000",
    "app.ip-check.enabled=true",
    "minio.endpoint=http://localhost:9000",
    "minio.bucket-name=test-bucket"
})
class AttendanceHybridValidationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private ValidMacRepository validMacRepository;
    @Autowired private ValidIpRepository validIpRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private OtRecordRepository otRecordRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String employeeToken;
    private User employee;
    private User admin;

    private static final String FAKE_PHOTO = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2Q==";
    private static final String VALID_BSSID = "AA:BB:CC:DD:EE:FF";
    private static final String CLIENT_IP = "203.0.113.10";

    @BeforeEach
    void setUp() throws Exception {
        // OT records FK to attendance records — delete first to avoid FK violations when a
        // check-out test lands after shift-end + otBuffer and creates an OT record.
        otRecordRepository.deleteAll();
        attendanceRecordRepository.deleteAll();
        validMacRepository.deleteAll();
        validIpRepository.deleteAll();
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

        admin = userRepository.save(User.builder()
            .username("hybrid_admin")
            .email("hybrid_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Hybrid Admin")
            .role(UserRole.ADMIN)
            .build());

        employee = userRepository.save(User.builder()
            .username("hybrid_employee")
            .email("hybrid_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Hybrid Employee")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build());

        employeeToken = loginAndGetToken("hybrid_employee", "emp123");
    }

    // ── Mobile path: bssid present → validate against valid_macs ────────────

    @Test
    void checkIn_validBssid_returns201() throws Exception {
        validMacRepository.save(ValidMac.builder()
            .bssid(VALID_BSSID)
            .createdBy("admin")
            .build());

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, VALID_BSSID));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated());
    }

    @Test
    void checkIn_bssidLowerCase_normalizedAndMatched_returns201() throws Exception {
        validMacRepository.save(ValidMac.builder()
            .bssid(VALID_BSSID)
            .createdBy("admin")
            .build());

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, "aa:bb:cc:dd:ee:ff"));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated());
    }

    @Test
    void checkIn_unknownBssid_returns403WithINVALID_MAC() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, "11:22:33:44:55:66"));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("INVALID_MAC"));
    }

    @Test
    void checkIn_inactiveBssid_returns403WithINVALID_MAC() throws Exception {
        validMacRepository.save(ValidMac.builder()
            .bssid(VALID_BSSID)
            .createdBy("admin")
            .active(false)
            .build());

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, VALID_BSSID));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("INVALID_MAC"));
    }

    @Test
    void checkIn_validBssidButInvalidClientIp_stillSucceeds_bssidTakesPriority() throws Exception {
        // No valid_ips configured at all — proves the MAC branch is used exclusively
        // and IP is never consulted when bssid is present.
        validMacRepository.save(ValidMac.builder()
            .bssid(VALID_BSSID)
            .createdBy("admin")
            .build());

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, VALID_BSSID));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr("1.2.3.4"); return req; }))
            .andExpect(status().isCreated());
    }

    // ── Web path: bssid null/blank → validate against valid_ips (unchanged) ──

    @Test
    void checkIn_nullBssid_fallsBackToIpValidation_validIp_returns201() throws Exception {
        validIpRepository.save(ValidIp.builder()
            .ipAddress(CLIENT_IP)
            .scope(IpScope.COMPANY)
            .createdBy(admin)
            .build());

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr(CLIENT_IP); return req; }))
            .andExpect(status().isCreated());
    }

    @Test
    void checkIn_blankBssid_fallsBackToIpValidation_invalidIp_returns403WithINVALID_IP() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, "  "));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr("9.9.9.9"); return req; }))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("INVALID_IP"));
    }

    @Test
    void checkIn_nullBssid_unknownIp_returns403WithINVALID_IP() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr("9.9.9.9"); return req; }))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("INVALID_IP"));
    }

    // ── Client site mode bypasses both checks entirely ───────────────────────

    @Test
    void checkIn_clientSiteMode_skipsBothMacAndIpValidation() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(10.77, 106.69, FAKE_PHOTO, true, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr("9.9.9.9"); return req; }))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isClientSite").value(true));
    }

    // ── Check-out mirrors the check-in validation path (Epic 10 P0 fix) ──────
    //
    // Bug: AttendanceService.checkOut() previously always validated against
    // valid_ips regardless of how check-in was validated, so an employee who
    // checked in via BSSID (no valid_ips entry needed) would be wrongly
    // rejected — or worse, a stale/foreign IP could pass — at check-out.
    // Fix: the validation method used at check-in is persisted on the record
    // and re-applied at check-out.

    @Test
    void checkOut_afterBssidCheckIn_matchingBssid_returns200() throws Exception {
        validMacRepository.save(ValidMac.builder()
            .bssid(VALID_BSSID)
            .createdBy("admin")
            .build());

        String checkInBody = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, VALID_BSSID));
        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInBody)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated());

        // No valid_ips registered at all — proves check-out used the BSSID path, not IP.
        String checkOutBody = objectMapper.writeValueAsString(
            new CheckOutRequest(null, null, FAKE_PHOTO, VALID_BSSID));
        mockMvc.perform(post("/api/attendance/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkOutBody)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr("1.2.3.4"); return req; }))
            .andExpect(status().isOk());
    }

    @Test
    void checkOut_afterBssidCheckIn_missingBssid_returns403WithINVALID_MAC() throws Exception {
        validMacRepository.save(ValidMac.builder()
            .bssid(VALID_BSSID)
            .createdBy("admin")
            .build());

        String checkInBody = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, VALID_BSSID));
        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInBody)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated());

        String checkOutBody = objectMapper.writeValueAsString(
            new CheckOutRequest(null, null, FAKE_PHOTO, null));
        mockMvc.perform(post("/api/attendance/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkOutBody)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("INVALID_MAC"));
    }

    @Test
    void checkOut_afterBssidCheckIn_wrongBssid_returns403WithINVALID_MAC() throws Exception {
        validMacRepository.save(ValidMac.builder()
            .bssid(VALID_BSSID)
            .createdBy("admin")
            .build());

        String checkInBody = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, VALID_BSSID));
        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInBody)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated());

        String checkOutBody = objectMapper.writeValueAsString(
            new CheckOutRequest(null, null, FAKE_PHOTO, "11:22:33:44:55:66"));
        mockMvc.perform(post("/api/attendance/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkOutBody)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("INVALID_MAC"));
    }

    @Test
    void checkOut_afterIpCheckIn_validIp_returns200() throws Exception {
        validIpRepository.save(ValidIp.builder()
            .ipAddress(CLIENT_IP)
            .scope(IpScope.COMPANY)
            .createdBy(admin)
            .build());

        String checkInBody = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, null));
        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInBody)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr(CLIENT_IP); return req; }))
            .andExpect(status().isCreated());

        String checkOutBody = objectMapper.writeValueAsString(
            new CheckOutRequest(null, null, FAKE_PHOTO, null));
        mockMvc.perform(post("/api/attendance/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkOutBody)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr(CLIENT_IP); return req; }))
            .andExpect(status().isOk());
    }

    @Test
    void checkOut_afterIpCheckIn_unknownIp_returns403WithINVALID_IP() throws Exception {
        validIpRepository.save(ValidIp.builder()
            .ipAddress(CLIENT_IP)
            .scope(IpScope.COMPANY)
            .createdBy(admin)
            .build());

        String checkInBody = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, null));
        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInBody)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr(CLIENT_IP); return req; }))
            .andExpect(status().isCreated());

        String checkOutBody = objectMapper.writeValueAsString(
            new CheckOutRequest(null, null, FAKE_PHOTO, null));
        mockMvc.perform(post("/api/attendance/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkOutBody)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr("9.9.9.9"); return req; }))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("INVALID_IP"));
    }

    @Test
    void checkOut_afterClientSiteCheckIn_skipsBothMacAndIpValidation() throws Exception {
        String checkInBody = objectMapper.writeValueAsString(
            new CheckInRequest(10.77, 106.69, FAKE_PHOTO, true, null));
        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInBody)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr("9.9.9.9"); return req; }))
            .andExpect(status().isCreated());

        String checkOutBody = objectMapper.writeValueAsString(
            new CheckOutRequest(10.77, 106.69, FAKE_PHOTO, null));
        mockMvc.perform(post("/api/attendance/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkOutBody)
                .header("Authorization", "Bearer " + employeeToken)
                .with(req -> { req.setRemoteAddr("9.9.9.9"); return req; }))
            .andExpect(status().isOk());
    }
}
