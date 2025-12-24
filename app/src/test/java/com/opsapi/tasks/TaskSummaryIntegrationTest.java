package com.opsapi.tasks;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerResponse;
import com.opsapi.notes.dto.NoteCreateRequest;
import com.opsapi.tasks.dto.TaskCreateRequest;
import com.opsapi.tasks.dto.TaskResponse;
import com.opsapi.tasks.dto.TaskSummaryResponse;
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
class TaskSummaryIntegrationTest {

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
    void summary_returnsLatestNotePerTask() throws Exception {
        String baseUrl = "http://localhost:" + port;

        // customer
        ResponseEntity<CustomerResponse> cResp = rest.postForEntity(
                baseUrl + "/customers",
                new CustomerCreateRequest("Summary Owner", "summary-owner@example.com"),
                CustomerResponse.class
        );
        assertThat(cResp.getStatusCode().value()).isEqualTo(201);
        UUID customerId = cResp.getBody().getId();

        // task A
        UUID taskA = rest.postForEntity(
                baseUrl + "/customers/" + customerId + "/tasks",
                new TaskCreateRequest("Task A", false),
                TaskResponse.class
        ).getBody().getId();

        // task B
        UUID taskB = rest.postForEntity(
                baseUrl + "/customers/" + customerId + "/tasks",
                new TaskCreateRequest("Task B", false),
                TaskResponse.class
        ).getBody().getId();

        // Add notes: make sure each task has a distinct "latest"
        rest.postForEntity(baseUrl + "/tasks/" + taskA + "/notes", new NoteCreateRequest("A1"), String.class);
        Thread.sleep(10);
        rest.postForEntity(baseUrl + "/tasks/" + taskA + "/notes", new NoteCreateRequest("A2-latest"), String.class);

        rest.postForEntity(baseUrl + "/tasks/" + taskB + "/notes", new NoteCreateRequest("B1-latest"), String.class);

        // Call summary
        ResponseEntity<TaskSummaryResponse> sResp = rest.getForEntity(
                baseUrl + "/customers/" + customerId + "/tasks/summary",
                TaskSummaryResponse.class
        );

        assertThat(sResp.getStatusCode().value()).isEqualTo(200);
        TaskSummaryResponse body = sResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCount()).isEqualTo(2);

        // Verify each task has correct latest snippet
        var items = body.getItems();

        // Find Task A item
        var aItem = items.stream().filter(i -> i.getId().equals(taskA)).findFirst().orElseThrow();
        assertThat(aItem.getLatestNoteSnippet()).isEqualTo("A2-latest");

        // Find Task B item
        var bItem = items.stream().filter(i -> i.getId().equals(taskB)).findFirst().orElseThrow();
        assertThat(bItem.getLatestNoteSnippet()).isEqualTo("B1-latest");
    }
}