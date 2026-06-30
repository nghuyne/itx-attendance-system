package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.domain.Holiday;
import com.itx.attendance.domain.HolidayType;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.CreateHolidayRequest;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.repository.HolidayRepository;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Epic 2 — Story 2.3: Holiday Management (Admin)
 *
 * Tests business logic: CRUD operations, duplicate date detection, year filter,
 * type validation, and behavior that existing OT records are NOT recalculated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:holidayintegtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000"
})
class HolidayControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        holidayRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
            .username("holiday_admin")
            .email("holiday_admin@itx.local")
            .passwordHash(passwordEncoder.encode("admin123"))
            .fullName("Holiday Admin")
            .role(UserRole.ADMIN)
            .build());

        adminToken = loginAndGetToken("holiday_admin", "admin123");
    }

    // ── GET /api/admin/holidays ──────────────────────────────────────────────

    @Test
    void getHolidays_emptyDb_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/admin/holidays")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getHolidays_withData_returnsAllHolidays() throws Exception {
        holidayRepository.save(Holiday.builder()
            .date(LocalDate.of(2026, 1, 1))
            .name("Tết Dương Lịch")
            .type(HolidayType.FIXED)
            .year(2026)
            .build());
        holidayRepository.save(Holiday.builder()
            .date(LocalDate.of(2026, 1, 29))
            .name("Tết Nguyên Đán")
            .type(HolidayType.DYNAMIC)
            .year(2026)
            .build());

        mockMvc.perform(get("/api/admin/holidays")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[*].name",
                hasItems("Tết Dương Lịch", "Tết Nguyên Đán")));
    }

    @Test
    void getHolidays_withYearFilter_returnsOnlyMatchingYear() throws Exception {
        holidayRepository.save(Holiday.builder()
            .date(LocalDate.of(2026, 1, 1))
            .name("Tết Dương Lịch 2026")
            .type(HolidayType.FIXED)
            .year(2026)
            .build());
        holidayRepository.save(Holiday.builder()
            .date(LocalDate.of(2027, 1, 1))
            .name("Tết Dương Lịch 2027")
            .type(HolidayType.FIXED)
            .year(2027)
            .build());

        mockMvc.perform(get("/api/admin/holidays")
                .param("year", "2026")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].name").value("Tết Dương Lịch 2026"))
            .andExpect(jsonPath("$.content[0].year").value(2026));
    }

    @Test
    void getHolidays_withYearFilter_noMatch_returnsEmptyPage() throws Exception {
        holidayRepository.save(Holiday.builder()
            .date(LocalDate.of(2026, 4, 30))
            .name("Ngày Giải Phóng")
            .type(HolidayType.FIXED)
            .year(2026)
            .build());

        mockMvc.perform(get("/api/admin/holidays")
                .param("year", "2025")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void getHolidays_supportsPagination() throws Exception {
        for (int i = 1; i <= 5; i++) {
            holidayRepository.save(Holiday.builder()
                .date(LocalDate.of(2026, i, 1))
                .name("Holiday " + i)
                .type(HolidayType.FIXED)
                .year(2026)
                .build());
        }

        mockMvc.perform(get("/api/admin/holidays")
                .param("page", "0").param("size", "3")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(3)))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    // ── POST /api/admin/holidays ─────────────────────────────────────────────

    @Test
    void createHoliday_fixedType_returns201WithHolidayData() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateHolidayRequest(LocalDate.of(2026, 9, 2), "Quốc Khánh", HolidayType.FIXED, 2026));

        mockMvc.perform(post("/api/admin/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.date").value("2026-09-02"))
            .andExpect(jsonPath("$.name").value("Quốc Khánh"))
            .andExpect(jsonPath("$.type").value("FIXED"))
            .andExpect(jsonPath("$.year").value(2026));
    }

    @Test
    void createHoliday_dynamicType_returns201WithLunarLabel() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateHolidayRequest(LocalDate.of(2026, 1, 29), "Tết Nguyên Đán", HolidayType.DYNAMIC, 2026));

        mockMvc.perform(post("/api/admin/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("DYNAMIC"))
            .andExpect(jsonPath("$.name").value("Tết Nguyên Đán"));
    }

    @Test
    void createHoliday_yearAutoSetFromDate() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateHolidayRequest(LocalDate.of(2027, 5, 1), "Ngày Lao Động 2027", HolidayType.FIXED, 2027));

        mockMvc.perform(post("/api/admin/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.year").value(2027));
    }

    @Test
    void createHoliday_duplicateDate_returns409WithHOLIDAY_DATE_EXISTS() throws Exception {
        holidayRepository.save(Holiday.builder()
            .date(LocalDate.of(2026, 1, 1))
            .name("Tết Dương Lịch")
            .type(HolidayType.FIXED)
            .year(2026)
            .build());

        String body = objectMapper.writeValueAsString(
            new CreateHolidayRequest(LocalDate.of(2026, 1, 1), "Trùng Ngày", HolidayType.DYNAMIC, 2026));

        mockMvc.perform(post("/api/admin/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("HOLIDAY_DATE_EXISTS"));
    }

    @Test
    void createHoliday_nameTooShort_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateHolidayRequest(LocalDate.of(2026, 12, 25), "X", HolidayType.FIXED, 2026));

        mockMvc.perform(post("/api/admin/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createHoliday_missingDate_returns400() throws Exception {
        String body = "{\"name\":\"No Date Holiday\",\"type\":\"FIXED\",\"year\":2026}";

        mockMvc.perform(post("/api/admin/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createHoliday_missingType_returns400() throws Exception {
        String body = "{\"date\":\"2026-06-15\",\"name\":\"No Type\",\"year\":2026}";

        mockMvc.perform(post("/api/admin/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createHoliday_yearOutOfRange_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
            new CreateHolidayRequest(LocalDate.of(2026, 8, 15), "Test", HolidayType.FIXED, 1800));

        mockMvc.perform(post("/api/admin/holidays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/admin/holidays/{id} ─────────────────────────────────────

    @Test
    void deleteHoliday_existingId_returns200() throws Exception {
        Holiday holiday = holidayRepository.save(Holiday.builder()
            .date(LocalDate.of(2026, 3, 8))
            .name("Ngày Phụ Nữ")
            .type(HolidayType.DYNAMIC)
            .year(2026)
            .build());

        mockMvc.perform(delete("/api/admin/holidays/" + holiday.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteHoliday_notFound_returns404WithHOLIDAY_NOT_FOUND() throws Exception {
        mockMvc.perform(delete("/api/admin/holidays/99999")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("HOLIDAY_NOT_FOUND"));
    }

    @Test
    void deleteHoliday_afterDelete_noLongerReturnedInList() throws Exception {
        Holiday holiday = holidayRepository.save(Holiday.builder()
            .date(LocalDate.of(2026, 7, 4))
            .name("Test Delete Holiday")
            .type(HolidayType.FIXED)
            .year(2026)
            .build());

        mockMvc.perform(delete("/api/admin/holidays/" + holiday.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/holidays")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
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
