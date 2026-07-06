package com.itx.attendance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:healthtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.password=test",
    "spring.flyway.enabled=false",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000",
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=minioadmin",
    "minio.secret-key=minioadmin",
    "minio.bucket-name=test-bucket"
})
class HealthEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturns200WithStatusUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health",
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }
}
