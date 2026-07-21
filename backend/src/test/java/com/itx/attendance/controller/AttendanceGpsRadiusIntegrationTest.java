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
import java.time.LocalTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Epic 7 — Story 7.1: GPS Office Radius Validation applied during check-in.
 *
 * An active OfficeLocation (Văn phòng chính, 10.77695/106.70070, radius 200m) is seeded before
 * each test. Coordinates ~2km away are used to simulate an out-of-radius check-in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:attendancegpsradiustestdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.password=test",
    "spring.flyway.enabled=false",
    "app.rate-limit.login.max-attempts=1000",
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
class AttendanceGpsRadiusIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private OfficeLocationRepository officeLocationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String employeeToken;

    private static final String FAKE_PHOTO = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2Q==";

    // Office location center. Kept to 2 decimal places: the test schema is generated via
    // ddl-auto=create-drop from the OfficeLocation entity (no @Column precision/scale set),
    // which defaults to NUMERIC(19,2) in H2 — finer-grained coordinates get rounded on save,
    // shifting the stored point and producing false "outside radius" failures.
    private static final double OFFICE_LAT = 10.78;
    private static final double OFFICE_LNG = 106.70;

    // ~13km away from OFFICE_LAT/LNG — well outside a 200m radius
    private static final double FAR_LAT = 10.90;
    private static final double FAR_LNG = 106.70;

    @BeforeEach
    void setUp() throws Exception {
        attendanceRecordRepository.deleteAll();
        userRepository.deleteAll();
        shiftRepository.deleteAll();
        officeLocationRepository.deleteAll();

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

        userRepository.save(User.builder()
            .username("gps_employee")
            .email("gps_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("GPS Employee")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build());

        employeeToken = loginAndGetToken("gps_employee", "emp123");

        officeLocationRepository.save(OfficeLocation.builder()
            .name("Văn phòng chính")
            .latitude(BigDecimal.valueOf(OFFICE_LAT))
            .longitude(BigDecimal.valueOf(OFFICE_LNG))
            .radiusMeters(200)
            .active(true)
            .build());
    }

    @Test
    void checkIn_officeMode_withinRadius_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(OFFICE_LAT, OFFICE_LNG, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated());
    }

    @Test
    void checkIn_officeMode_outsideRadius_returns400WithOUT_OF_OFFICE_RADIUS() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(FAR_LAT, FAR_LNG, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("OUT_OF_OFFICE_RADIUS"))
            .andExpect(jsonPath("$.message", containsString("m)")));
    }

    @Test
    void checkIn_clientSiteMode_outsideRadius_bypassesRadiusCheck_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(FAR_LAT, FAR_LNG, FAKE_PHOTO, true, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isClientSite").value(true));
    }

    @Test
    void checkIn_officeMode_noActiveLocations_bypassesRadiusCheck_returns201() throws Exception {
        officeLocationRepository.deleteAll();

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(FAR_LAT, FAR_LNG, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated());
    }

    @Test
    void checkIn_officeMode_inactiveLocationIgnored_outsideRadius_returns400() throws Exception {
        officeLocationRepository.deleteAll();
        officeLocationRepository.save(OfficeLocation.builder()
            .name("Văn phòng cũ (đã tắt)")
            .latitude(BigDecimal.valueOf(FAR_LAT))
            .longitude(BigDecimal.valueOf(FAR_LNG))
            .radiusMeters(5000)
            .active(false)
            .build());
        officeLocationRepository.save(OfficeLocation.builder()
            .name("Văn phòng chính")
            .latitude(BigDecimal.valueOf(OFFICE_LAT))
            .longitude(BigDecimal.valueOf(OFFICE_LNG))
            .radiusMeters(200)
            .active(true)
            .build());

        String body = objectMapper.writeValueAsString(
            new CheckInRequest(FAR_LAT, FAR_LNG, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("OUT_OF_OFFICE_RADIUS"));
    }

    @Test
    void checkIn_officeMode_gpsNull_bypassesRadiusCheck_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CheckInRequest(null, null, FAKE_PHOTO, false, null));

        mockMvc.perform(post("/api/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.gpsUnavailable").value(true));
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
