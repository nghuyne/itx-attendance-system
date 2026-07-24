package com.itx.attendance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.security.LoginRateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Common Spring context wiring shared by every MockMvc controller integration test:
 * H2 driver/credentials, JWT secret/expiration, MinIO test credentials, the
 * login-and-extract-token helper, and a per-method database reset. Subclasses must
 * still declare their own {@code @TestPropertySource}, at minimum a
 * {@code spring.datasource.url}; classes with an identical extra-property set (same
 * keys, same values, same array order — grep for the DB name to see who else shares
 * it) share one H2 DB name so Spring reuses a single cached context across them
 * instead of booting one per class. Isolation no longer depends on DB separation:
 * {@link #resetTestState()} truncates every app table and clears the login
 * rate-limiter's in-memory buckets before each test method, regardless of which
 * class or context it runs in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.password=test",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "minio.access-key=minioadmin",
    "minio.secret-key=minioadmin",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000",
    "app.rate-limit.login.test-reset-enabled=true"
})
abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected LoginRateLimitFilter loginRateLimitFilter;

    /**
     * Runs before every test method (superclass {@code @BeforeEach} methods execute
     * before subclass ones per JUnit 5), so each test starts from an empty schema no
     * matter which sibling class shares this context or what a previous test committed
     * via {@code @Transactional(REQUIRES_NEW)} (NotificationService, HolidayService,
     * AuditLogRepository). The table list is read from the live schema each time (not
     * hardcoded) so a future Flyway/entity addition can't silently escape the reset.
     * {@code TRUNCATE} (not {@code DELETE}) also restarts each table's identity
     * sequence, so generated IDs are stable per test method instead of climbing across
     * every method that shares this context. Referential integrity is toggled off
     * around the truncates, in a try/finally, so table order doesn't matter and a
     * mid-loop failure can't leave it permanently disabled for the rest of the suite.
     * Also clears the rate-limiter's in-memory buckets — DB truncation alone doesn't
     * touch that singleton's state, and without this a class sharing a context with
     * heavy login traffic (e.g. LoginRateLimiterIntegrationTest alongside
     * AuthControllerTest/AuthPasswordFlowTest) can find itself pre-rate-limited by a
     * previous class's logins.
     */
    @BeforeEach
    void resetTestState() {
        List<String> tables = jdbcTemplate.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'", String.class);
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            tables.forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE " + table));
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
        loginRateLimitFilter.resetForTests();
    }

    protected String loginAndGetToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
