package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.OfficeLocation;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.CreateOfficeLocationRequest;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.OfficeLocationRepository;
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

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 7 — Story 7.1: GPS Office Radius Validation (Admin CRUD).
 *
 * Covers: office-location CRUD, radius bounds validation (INVALID_RADIUS), not-found handling,
 * and role-based access (ADMIN only).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:officelocationtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
class OfficeLocationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private OfficeLocationRepository officeLocationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;
    private String employeeToken;

    @BeforeEach
    void setUp() throws Exception {
        officeLocationRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
            .username("loc_admin")
            .email("loc_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Location Admin")
            .role(UserRole.ADMIN)
            .build());

        userRepository.save(User.builder()
            .username("loc_employee")
            .email("loc_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("Location Employee")
            .role(UserRole.EMPLOYEE)
            .build());

        adminToken = loginAndGetToken("loc_admin", "admin123");
        employeeToken = loginAndGetToken("loc_employee", "emp123");
    }

    // ── GET /api/admin/office-locations ─────────────────────────────────────

    @Test
    void getOfficeLocations_emptyDb_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/admin/office-locations")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getOfficeLocations_withData_returnsOrderedByName() throws Exception {
        officeLocationRepository.save(OfficeLocation.builder()
            .name("Văn phòng Z").latitude(BigDecimal.valueOf(10.77)).longitude(BigDecimal.valueOf(106.70))
            .radiusMeters(200).active(true).build());
        officeLocationRepository.save(OfficeLocation.builder()
            .name("Văn phòng A").latitude(BigDecimal.valueOf(10.78)).longitude(BigDecimal.valueOf(106.71))
            .radiusMeters(300).active(true).build());

        mockMvc.perform(get("/api/admin/office-locations")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].name").value("Văn phòng A"))
            .andExpect(jsonPath("$[1].name").value("Văn phòng Z"));
    }

    @Test
    void getOfficeLocations_employeeRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/office-locations")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    // ── POST /api/admin/office-locations ────────────────────────────────────

    @Test
    void createOfficeLocation_validPayload_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("Văn phòng chính", BigDecimal.valueOf(10.77695), BigDecimal.valueOf(106.70070), 200, null));

        mockMvc.perform(post("/api/admin/office-locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("Văn phòng chính"))
            .andExpect(jsonPath("$.radiusMeters").value(200))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createOfficeLocation_radiusBelowMin_returns400WithINVALID_RADIUS() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("Văn phòng nhỏ", BigDecimal.valueOf(10.77), BigDecimal.valueOf(106.70), 49, null));

        mockMvc.perform(post("/api/admin/office-locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_RADIUS"));
    }

    @Test
    void createOfficeLocation_radiusAboveMax_returns400WithINVALID_RADIUS() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("Văn phòng lớn", BigDecimal.valueOf(10.77), BigDecimal.valueOf(106.70), 5001, null));

        mockMvc.perform(post("/api/admin/office-locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_RADIUS"));
    }

    @Test
    void createOfficeLocation_boundaryRadius50_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("Văn phòng biên dưới", BigDecimal.valueOf(10.77), BigDecimal.valueOf(106.70), 50, null));

        mockMvc.perform(post("/api/admin/office-locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.radiusMeters").value(50));
    }

    @Test
    void createOfficeLocation_boundaryRadius5000_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("Văn phòng biên trên", BigDecimal.valueOf(10.77), BigDecimal.valueOf(106.70), 5000, null));

        mockMvc.perform(post("/api/admin/office-locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.radiusMeters").value(5000));
    }

    @Test
    void createOfficeLocation_missingName_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("", BigDecimal.valueOf(10.77), BigDecimal.valueOf(106.70), 200, null));

        mockMvc.perform(post("/api/admin/office-locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createOfficeLocation_latitudeOutOfRange_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("Văn phòng lỗi", BigDecimal.valueOf(95.0), BigDecimal.valueOf(106.70), 200, null));

        mockMvc.perform(post("/api/admin/office-locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createOfficeLocation_employeeRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("Văn phòng", BigDecimal.valueOf(10.77), BigDecimal.valueOf(106.70), 200, null));

        mockMvc.perform(post("/api/admin/office-locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    // ── PUT /api/admin/office-locations/{id} ────────────────────────────────

    @Test
    void updateOfficeLocation_existingId_returns200WithUpdatedFields() throws Exception {
        OfficeLocation location = officeLocationRepository.save(OfficeLocation.builder()
            .name("Cũ").latitude(BigDecimal.valueOf(10.77)).longitude(BigDecimal.valueOf(106.70))
            .radiusMeters(200).active(true).build());

        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("Mới", BigDecimal.valueOf(10.80), BigDecimal.valueOf(106.75), 300, false));

        mockMvc.perform(put("/api/admin/office-locations/" + location.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Mới"))
            .andExpect(jsonPath("$.radiusMeters").value(300))
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateOfficeLocation_notFound_returns404WithOFFICE_LOCATION_NOT_FOUND() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("Mới", BigDecimal.valueOf(10.80), BigDecimal.valueOf(106.75), 300, false));

        mockMvc.perform(put("/api/admin/office-locations/99999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("OFFICE_LOCATION_NOT_FOUND"));
    }

    @Test
    void updateOfficeLocation_invalidRadius_returns400WithINVALID_RADIUS() throws Exception {
        OfficeLocation location = officeLocationRepository.save(OfficeLocation.builder()
            .name("VP").latitude(BigDecimal.valueOf(10.77)).longitude(BigDecimal.valueOf(106.70))
            .radiusMeters(200).active(true).build());

        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("VP", BigDecimal.valueOf(10.77), BigDecimal.valueOf(106.70), 10, null));

        mockMvc.perform(put("/api/admin/office-locations/" + location.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_RADIUS"));
    }

    @Test
    void updateOfficeLocation_isActiveNull_keepsExistingActiveState() throws Exception {
        OfficeLocation location = officeLocationRepository.save(OfficeLocation.builder()
            .name("VP").latitude(BigDecimal.valueOf(10.77)).longitude(BigDecimal.valueOf(106.70))
            .radiusMeters(200).active(false).build());

        String body = objectMapper.writeValueAsString(
            new CreateOfficeLocationRequest("VP Sửa", BigDecimal.valueOf(10.77), BigDecimal.valueOf(106.70), 200, null));

        mockMvc.perform(put("/api/admin/office-locations/" + location.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    // ── DELETE /api/admin/office-locations/{id} ─────────────────────────────

    @Test
    void deleteOfficeLocation_existingId_returns204() throws Exception {
        OfficeLocation location = officeLocationRepository.save(OfficeLocation.builder()
            .name("VP Xóa").latitude(BigDecimal.valueOf(10.77)).longitude(BigDecimal.valueOf(106.70))
            .radiusMeters(200).active(true).build());

        mockMvc.perform(delete("/api/admin/office-locations/" + location.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteOfficeLocation_notFound_returns404WithOFFICE_LOCATION_NOT_FOUND() throws Exception {
        mockMvc.perform(delete("/api/admin/office-locations/99999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("OFFICE_LOCATION_NOT_FOUND"));
    }

    @Test
    void deleteOfficeLocation_employeeRole_returns403() throws Exception {
        OfficeLocation location = officeLocationRepository.save(OfficeLocation.builder()
            .name("VP").latitude(BigDecimal.valueOf(10.77)).longitude(BigDecimal.valueOf(106.70))
            .radiusMeters(200).active(true).build());

        mockMvc.perform(delete("/api/admin/office-locations/" + location.getId())
                .header("Authorization", "Bearer " + employeeToken))
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
