package com.opsapi.customers;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
    }

    @LocalServerPort
    int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void createThenFetchCustomer() {
        String baseUrl = "http://localhost:" + port;

        CustomerCreateRequest req = new CustomerCreateRequest("Grace Hopper", "grace@example.com");

        var createResp = rest.postForEntity(baseUrl + "/customers", req, CustomerResponse.class);
        assertThat(createResp.getStatusCode().value()).isEqualTo(201);

        CustomerResponse created = createResp.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getEmail()).isEqualTo("grace@example.com");

        var getResp = rest.getForEntity(baseUrl + "/customers/" + created.getId(), CustomerResponse.class);
        assertThat(getResp.getStatusCode().value()).isEqualTo(200);

        CustomerResponse fetched = getResp.getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.getName()).isEqualTo("Grace Hopper");
        assertThat(fetched.getEmail()).isEqualTo("grace@example.com");
    }
}