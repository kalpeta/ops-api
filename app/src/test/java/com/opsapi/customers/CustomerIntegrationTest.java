package com.opsapi.customers;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerResponse;
import com.opsapi.customers.dto.CustomerUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerIntegrationTest {

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
    void createUpdateDeleteCustomer_endToEnd() {
        String baseUrl = "http://localhost:" + port;

        // 1) CREATE
        CustomerCreateRequest createReq = new CustomerCreateRequest("Grace Hopper", "grace@example.com");
        ResponseEntity<CustomerResponse> createResp =
                rest.postForEntity(baseUrl + "/customers", createReq, CustomerResponse.class);

        assertThat(createResp.getStatusCode().value()).isEqualTo(201);

        CustomerResponse created = createResp.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Grace Hopper");
        assertThat(created.getEmail()).isEqualTo("grace@example.com");

        // 2) UPDATE
        CustomerUpdateRequest updateReq = new CustomerUpdateRequest("Updated Name", "updated@example.com");
        ResponseEntity<CustomerResponse> updateResp = rest.exchange(
                baseUrl + "/customers/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateReq),
                CustomerResponse.class
        );

        assertThat(updateResp.getStatusCode().value()).isEqualTo(200);
        CustomerResponse updated = updateResp.getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getEmail()).isEqualTo("updated@example.com");

        // 3) GET should return updated values
        ResponseEntity<CustomerResponse> getResp =
                rest.getForEntity(baseUrl + "/customers/" + created.getId(), CustomerResponse.class);

        assertThat(getResp.getStatusCode().value()).isEqualTo(200);
        CustomerResponse fetched = getResp.getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.getName()).isEqualTo("Updated Name");
        assertThat(fetched.getEmail()).isEqualTo("updated@example.com");

        // 4) DELETE
        ResponseEntity<Void> deleteResp = rest.exchange(
                baseUrl + "/customers/" + created.getId(),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );
        assertThat(deleteResp.getStatusCode().value()).isEqualTo(204);

        // 5) GET AFTER DELETE -> 404
        ResponseEntity<String> getAfterDeleteResp =
                rest.getForEntity(baseUrl + "/customers/" + created.getId(), String.class);

        assertThat(getAfterDeleteResp.getStatusCode().value()).isEqualTo(404);
        assertThat(getAfterDeleteResp.getBody()).contains("NOT_FOUND");
    }
}