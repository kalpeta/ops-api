package com.opsapi.customers;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerResponse;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled("...")
class CustomerDependencyCheckIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("opsdb")
            .withUsername("ops")
            .withPassword("ops");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
    }

    @LocalServerPort
    int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void dependencyCheck_okAndFail_propagatesStatusAndBody() {
        String baseUrl = "http://localhost:" + port;

        // create customer first
        CustomerCreateRequest createReq = new CustomerCreateRequest("Dep Check User", "depcheck@example.com");
        ResponseEntity<CustomerResponse> createResp =
                rest.postForEntity(baseUrl + "/customers", createReq, CustomerResponse.class);

        assertThat(createResp.getStatusCode().value()).isEqualTo(201);
        CustomerResponse created = createResp.getBody();
        assertThat(created).isNotNull();

        String corr = "it-corr-123";

        // OK
        HttpHeaders okHeaders = new HttpHeaders();
        okHeaders.set("X-Correlation-Id", corr);

        ResponseEntity<Map> okResp = rest.exchange(
                baseUrl + "/customers/" + created.getId() + "/dependency-check?mode=ok",
                HttpMethod.GET,
                new HttpEntity<>(okHeaders),
                Map.class
        );

        assertThat(okResp.getStatusCode().value()).isEqualTo(200);
        assertThat(okResp.getHeaders().getFirst("X-Correlation-Id")).isEqualTo(corr);
        assertThat(okResp.getBody()).isNotNull();
        assertThat(okResp.getBody().get("stub")).isEqualTo("dependency");

        // FAIL
        HttpHeaders failHeaders = new HttpHeaders();
        failHeaders.set("X-Correlation-Id", corr);

        ResponseEntity<Map> failResp = rest.exchange(
                baseUrl + "/customers/" + created.getId() + "/dependency-check?mode=fail",
                HttpMethod.GET,
                new HttpEntity<>(failHeaders),
                Map.class
        );

        assertThat(failResp.getStatusCode().value()).isEqualTo(503);
        assertThat(failResp.getHeaders().getFirst("X-Correlation-Id")).isEqualTo(corr);
        assertThat(failResp.getBody()).isNotNull();
        assertThat(failResp.getBody().get("error")).isEqualTo("SERVICE_UNAVAILABLE");
    }
}
