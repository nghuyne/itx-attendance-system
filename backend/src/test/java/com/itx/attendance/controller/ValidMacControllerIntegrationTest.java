package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.domain.ValidMac;
import com.itx.attendance.dto.request.CreateValidMacRequest;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.repository.ValidMacRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 10 — Story 10.2 & 10.4: Valid MACs (BSSID) Management (Admin)
 *
 * Tests business logic: CRUD operations, BSSID format validation, soft delete,
 * reactivation-on-re-add, and duplicate detection.
 */
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:validmactestdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "app.rate-limit.login.max-attempts=1000"
})
class ValidMacControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ValidMacRepository validMacRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        validMacRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
            .username("mac_admin")
            .email("mac_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("MAC Admin")
            .role(UserRole.ADMIN)
            .build());

        adminToken = loginAndGetToken("mac_admin", "admin123");
    }

    // ── GET /api/admin/valid-macs ────────────────────────────────────────────

    @Test
    void getValidMacs_emptyDb_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/admin/valid-macs")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getValidMacs_withData_returnsListDirectlyNotPage() throws Exception {
        validMacRepository.save(ValidMac.builder()
            .bssid("AA:BB:CC:DD:EE:FF")
            .description("Router tầng 2")
            .createdBy("mac_admin")
            .build());

        mockMvc.perform(get("/api/admin/valid-macs")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].bssid").value("AA:BB:CC:DD:EE:FF"))
            .andExpect(jsonPath("$[0].description").value("Router tầng 2"));
    }

    @Test
    void getValidMacs_excludesInactiveMacs() throws Exception {
        validMacRepository.save(ValidMac.builder()
            .bssid("11:22:33:44:55:66")
            .createdBy("mac_admin")
            .active(false)
            .build());

        mockMvc.perform(get("/api/admin/valid-macs")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── POST /api/admin/valid-macs ───────────────────────────────────────────

    @Test
    void createValidMac_validBssid_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidMacRequest("aa:bb:cc:dd:ee:ff", "Văn phòng HCM"));

        mockMvc.perform(post("/api/admin/valid-macs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.bssid").value("AA:BB:CC:DD:EE:FF"))
            .andExpect(jsonPath("$.description").value("Văn phòng HCM"))
            .andExpect(jsonPath("$.createdBy").value("mac_admin"));
    }

    @Test
    void createValidMac_normalizesToUpperCase() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidMacRequest("  aa:bb:cc:dd:ee:ff  ".strip(), null));

        mockMvc.perform(post("/api/admin/valid-macs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.bssid").value("AA:BB:CC:DD:EE:FF"));
    }

    @Test
    void createValidMac_invalidFormat_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidMacRequest("not-a-bssid", null));

        mockMvc.perform(post("/api/admin/valid-macs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createValidMac_blankBssid_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidMacRequest("", null));

        mockMvc.perform(post("/api/admin/valid-macs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createValidMac_duplicateActiveBssid_returns409WithDUPLICATE_MAC() throws Exception {
        validMacRepository.save(ValidMac.builder()
            .bssid("AA:BB:CC:DD:EE:FF")
            .createdBy("mac_admin")
            .build());

        String body = objectMapper.writeValueAsString(
            new CreateValidMacRequest("AA:BB:CC:DD:EE:FF", null));

        mockMvc.perform(post("/api/admin/valid-macs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("DUPLICATE_MAC"));
    }

    @Test
    void createValidMac_reAddInactiveBssid_reactivatesInsteadOfDuplicate() throws Exception {
        ValidMac inactive = validMacRepository.save(ValidMac.builder()
            .bssid("AA:BB:CC:DD:EE:FF")
            .createdBy("someone_else")
            .active(false)
            .build());

        String body = objectMapper.writeValueAsString(
            new CreateValidMacRequest("AA:BB:CC:DD:EE:FF", "Reactivated"));

        mockMvc.perform(post("/api/admin/valid-macs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(inactive.getId()))
            .andExpect(jsonPath("$.bssid").value("AA:BB:CC:DD:EE:FF"))
            .andExpect(jsonPath("$.description").value("Reactivated"))
            .andExpect(jsonPath("$.createdBy").value("mac_admin"));
    }

    // ── DELETE /api/admin/valid-macs/{id} ────────────────────────────────────

    @Test
    void deleteValidMac_existingId_returns204AndSoftDeletes() throws Exception {
        ValidMac mac = validMacRepository.save(ValidMac.builder()
            .bssid("AA:BB:CC:DD:EE:FF")
            .createdBy("mac_admin")
            .build());

        mockMvc.perform(delete("/api/admin/valid-macs/" + mac.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        ValidMac reloaded = validMacRepository.findById(mac.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(reloaded.isActive());
    }

    @Test
    void deleteValidMac_notFound_returns404WithMAC_NOT_FOUND() throws Exception {
        mockMvc.perform(delete("/api/admin/valid-macs/99999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("MAC_NOT_FOUND"));
    }

    @Test
    void deleteValidMac_alreadyInactive_returns404WithMAC_NOT_FOUND() throws Exception {
        ValidMac mac = validMacRepository.save(ValidMac.builder()
            .bssid("AA:BB:CC:DD:EE:FF")
            .createdBy("mac_admin")
            .active(false)
            .build());

        mockMvc.perform(delete("/api/admin/valid-macs/" + mac.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("MAC_NOT_FOUND"));
    }

    @Test
    void deleteValidMac_invalidPathId_returns400() throws Exception {
        mockMvc.perform(delete("/api/admin/valid-macs/0")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    // ── Authorization ─────────────────────────────────────────────────────────

    @Test
    void getValidMacs_employeeRole_returns403() throws Exception {
        userRepository.save(User.builder()
            .username("mac_employee")
            .email("mac_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("MAC Employee")
            .role(UserRole.EMPLOYEE)
            .build());
        String employeeToken = loginAndGetToken("mac_employee", "emp123");

        mockMvc.perform(get("/api/admin/valid-macs")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void getValidMacs_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/valid-macs"))
            .andExpect(status().isUnauthorized());
    }
}
