package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.IpScope;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.domain.ValidIp;
import com.itx.attendance.dto.request.CreateValidIpRequest;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.repository.ValidIpRepository;
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
 * Integration tests for Epic 2 — Story 2.2: Valid Public IP Management (Admin)
 *
 * Tests business logic: CRUD operations, IP format validation, scope rules, duplicate detection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:validiptestdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000"
})
class ValidIpControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ValidIpRepository validIpRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;
    private User adminUser;
    private User employeeUser;

    @BeforeEach
    void setUp() throws Exception {
        validIpRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
            .username("ip_admin")
            .email("ip_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("IP Admin")
            .role(UserRole.ADMIN)
            .build());

        employeeUser = userRepository.save(User.builder()
            .username("ip_employee")
            .email("ip_employee@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("IP Employee")
            .role(UserRole.EMPLOYEE)
            .build());

        adminToken = loginAndGetToken("ip_admin", "admin123");
    }

    // ── GET /api/admin/valid-ips ─────────────────────────────────────────────

    @Test
    void getValidIps_emptyDb_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/admin/valid-ips")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getValidIps_withData_returnsPaginatedList() throws Exception {
        validIpRepository.save(ValidIp.builder()
            .ipAddress("203.0.113.10")
            .scope(IpScope.COMPANY)
            .createdBy(adminUser)
            .build());
        validIpRepository.save(ValidIp.builder()
            .ipAddress("198.51.100.5")
            .scope(IpScope.INDIVIDUAL)
            .employee(employeeUser)
            .createdBy(adminUser)
            .build());

        mockMvc.perform(get("/api/admin/valid-ips")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[*].ipAddress",
                hasItems("203.0.113.10", "198.51.100.5")));
    }

    // ── POST /api/admin/valid-ips ────────────────────────────────────────────

    @Test
    void createValidIp_companyScope_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("203.0.113.10", IpScope.COMPANY, null, "Văn phòng HCM"));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.ipAddress").value("203.0.113.10"))
            .andExpect(jsonPath("$.scope").value("COMPANY"))
            .andExpect(jsonPath("$.employeeId").doesNotExist())
            .andExpect(jsonPath("$.description").value("Văn phòng HCM"));
    }

    @Test
    void createValidIp_individualScope_returns201WithEmployeeLink() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("192.168.1.100", IpScope.INDIVIDUAL, employeeUser.getId(), null));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scope").value("INDIVIDUAL"))
            .andExpect(jsonPath("$.employeeId").value(employeeUser.getId()))
            .andExpect(jsonPath("$.employeeName").value("IP Employee"));
    }

    @Test
    void createValidIp_individualScope_missingEmployeeId_returns400WithEMPLOYEE_ID_REQUIRED() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("10.0.0.1", IpScope.INDIVIDUAL, null, null));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("EMPLOYEE_ID_REQUIRED"));
    }

    @Test
    void createValidIp_individualScope_nonExistentEmployee_returns404WithEMPLOYEE_NOT_FOUND() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("10.0.0.2", IpScope.INDIVIDUAL, "nonexistent-emp", null));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("EMPLOYEE_NOT_FOUND"));
    }

    @Test
    void createValidIp_invalidIpFormat_returns400WithINVALID_IP_FORMAT() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("not-an-ip", IpScope.COMPANY, null, null));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_IP_FORMAT"));
    }

    @Test
    void createValidIp_invalidIpOctetOutOfRange_returns400WithINVALID_IP_FORMAT() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("999.168.1.1", IpScope.COMPANY, null, null));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_IP_FORMAT"));
    }

    @Test
    void createValidIp_ipv6_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("2001:db8::1", IpScope.COMPANY, null, "IPv6 Office"));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ipAddress").value("2001:db8::1"));
    }

    @Test
    void createValidIp_duplicateCompanyIp_returns409WithDUPLICATE_IP() throws Exception {
        validIpRepository.save(ValidIp.builder()
            .ipAddress("203.0.113.10")
            .scope(IpScope.COMPANY)
            .createdBy(adminUser)
            .build());

        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("203.0.113.10", IpScope.COMPANY, null, null));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("DUPLICATE_IP"));
    }

    @Test
    void createValidIp_duplicateIndividualIpForSameEmployee_returns409WithDUPLICATE_IP() throws Exception {
        validIpRepository.save(ValidIp.builder()
            .ipAddress("192.168.1.100")
            .scope(IpScope.INDIVIDUAL)
            .employee(employeeUser)
            .createdBy(adminUser)
            .build());

        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("192.168.1.100", IpScope.INDIVIDUAL, employeeUser.getId(), null));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("DUPLICATE_IP"));
    }

    @Test
    void createValidIp_companyScopeWithEmployeeId_returns400WithEMPLOYEE_ID_NOT_ALLOWED() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("10.0.0.5", IpScope.COMPANY, employeeUser.getId(), null));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("EMPLOYEE_ID_NOT_ALLOWED"));
    }

    @Test
    void createValidIp_emptyIpAddress_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateValidIpRequest("", IpScope.COMPANY, null, null));

        mockMvc.perform(post("/api/admin/valid-ips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/admin/valid-ips/{id} ────────────────────────────────────

    @Test
    void deleteValidIp_existingId_returns204() throws Exception {
        ValidIp ip = validIpRepository.save(ValidIp.builder()
            .ipAddress("10.10.10.10")
            .scope(IpScope.COMPANY)
            .createdBy(adminUser)
            .build());

        mockMvc.perform(delete("/api/admin/valid-ips/" + ip.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteValidIp_notFound_returns404WithIP_NOT_FOUND() throws Exception {
        mockMvc.perform(delete("/api/admin/valid-ips/99999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("IP_NOT_FOUND"));
    }

    // ── GET /api/admin/employees ─────────────────────────────────────────────

    @Test
    void getEmployees_returnsOnlyActiveEmployees() throws Exception {
        userRepository.save(User.builder()
            .username("inactive_emp")
            .email("inactive@itx.local")
            .passwordHash(passwordEncoder.encode("pass"))
            .fullName("Inactive")
            .role(UserRole.EMPLOYEE)
            .active(false)
            .build());

        mockMvc.perform(get("/api/admin/employees")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].username", hasItem("ip_employee")))
            .andExpect(jsonPath("$[*].username", not(hasItem("inactive_emp"))));
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
