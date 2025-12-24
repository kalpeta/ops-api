package com.opsapi.notes;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerResponse;
import com.opsapi.notes.dto.NoteCreateRequest;
import com.opsapi.notes.dto.NoteListResponse;
import com.opsapi.tasks.dto.TaskCreateRequest;
import com.opsapi.tasks.dto.TaskResponse;
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
class NotesIntegrationTest {

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
    void createAndListNotes_newestFirst() throws Exception {
        String baseUrl = "http://localhost:" + port;

        // create customer
        ResponseEntity<CustomerResponse> cResp = rest.postForEntity(
                baseUrl + "/customers",
                new CustomerCreateRequest("Notes Owner", "notes-owner@example.com"),
                CustomerResponse.class
        );
        assertThat(cResp.getStatusCode().value()).isEqualTo(201);
        UUID customerId = cResp.getBody().getId();

        // create task
        ResponseEntity<TaskResponse> tResp = rest.postForEntity(
                baseUrl + "/customers/" + customerId + "/tasks",
                new TaskCreateRequest("Notes task", false),
                TaskResponse.class
        );
        assertThat(tResp.getStatusCode().value()).isEqualTo(201);
        UUID taskId = tResp.getBody().getId();

        // note 1
        rest.postForEntity(
                baseUrl + "/tasks/" + taskId + "/notes",
                new NoteCreateRequest("First note"),
                String.class
        );

        Thread.sleep(10);

        // note 2 (newer)
        rest.postForEntity(
                baseUrl + "/tasks/" + taskId + "/notes",
                new NoteCreateRequest("Second note"),
                String.class
        );

        // list
        ResponseEntity<NoteListResponse> listResp = rest.getForEntity(
                baseUrl + "/tasks/" + taskId + "/notes",
                NoteListResponse.class
        );

        assertThat(listResp.getStatusCode().value()).isEqualTo(200);
        NoteListResponse body = listResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCount()).isEqualTo(3); // includes "Task created" note from task creation
        assertThat(body.getItems().get(0).getBody()).isEqualTo("Second note");
        assertThat(body.getItems().get(1).getBody()).isEqualTo("First note");
    }
}