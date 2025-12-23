package com.opsapi.tasks;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerResponse;
import com.opsapi.tasks.dto.TaskCreateRequest;
import com.opsapi.tasks.dto.TaskListResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskListIntegrationTest {

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
    void listTasks_returnsNewestFirst() throws Exception {
        String baseUrl = "http://localhost:" + port;

        // Create customer
        ResponseEntity<CustomerResponse> cResp = rest.postForEntity(
                baseUrl + "/customers",
                new CustomerCreateRequest("List Owner", "list-owner@example.com"),
                CustomerResponse.class
        );
        assertThat(cResp.getStatusCode().value()).isEqualTo(201);
        UUID customerId = cResp.getBody().getId();

        // Create Task 1
        rest.postForEntity(
                baseUrl + "/customers/" + customerId + "/tasks",
                new TaskCreateRequest("Task 1", false),
                String.class
        );

        // ensure different created_at order
        Thread.sleep(10);

        // Create Task 2 (newer)
        rest.postForEntity(
                baseUrl + "/customers/" + customerId + "/tasks",
                new TaskCreateRequest("Task 2", false),
                String.class
        );

        // List tasks
        ResponseEntity<TaskListResponse> listResp = rest.getForEntity(
                baseUrl + "/customers/" + customerId + "/tasks",
                TaskListResponse.class
        );

        assertThat(listResp.getStatusCode().value()).isEqualTo(200);
        TaskListResponse body = listResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCount()).isEqualTo(2);
        assertThat(body.getItems().get(0).getTitle()).isEqualTo("Task 2");
        assertThat(body.getItems().get(1).getTitle()).isEqualTo("Task 1");
    }
}