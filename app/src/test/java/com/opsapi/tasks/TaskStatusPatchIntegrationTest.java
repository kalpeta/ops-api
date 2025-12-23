package com.opsapi.tasks;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerResponse;
import com.opsapi.notes.NoteRepository;
import com.opsapi.tasks.dto.TaskCreateRequest;
import com.opsapi.tasks.dto.TaskResponse;
import com.opsapi.tasks.dto.TaskStatusUpdateRequest;
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
import org.springframework.web.client.HttpServerErrorException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskStatusPatchIntegrationTest {

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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext ctx;

    private <T> T bean(Class<T> t) {
        return ctx.getBean(t);
    }

    @Test
    void patchStatus_updatesTask_andWritesAuditNote_andRollbackWorks() {
        String baseUrl = "http://localhost:" + port;

        // Create customer
        ResponseEntity<CustomerResponse> cResp = rest.postForEntity(
                baseUrl + "/customers",
                new CustomerCreateRequest("Patch Owner", "patch-owner@example.com"),
                CustomerResponse.class
        );
        assertThat(cResp.getStatusCode().value()).isEqualTo(201);
        UUID customerId = cResp.getBody().getId();

        // Create task
        ResponseEntity<TaskResponse> tResp = rest.postForEntity(
                baseUrl + "/customers/" + customerId + "/tasks",
                new TaskCreateRequest("Patch task", false),
                TaskResponse.class
        );
        assertThat(tResp.getStatusCode().value()).isEqualTo(201);
        UUID taskId = tResp.getBody().getId();

        TaskRepository taskRepo = bean(TaskRepository.class);
        NoteRepository noteRepo = bean(NoteRepository.class);

        long notesBefore = noteRepo.count();

        // PATCH success
        ResponseEntity<TaskResponse> patchResp = rest.exchange(
                baseUrl + "/tasks/" + taskId,
                HttpMethod.PATCH,
                new HttpEntity<>(new TaskStatusUpdateRequest("IN_PROGRESS", false)),
                TaskResponse.class
        );
        assertThat(patchResp.getStatusCode().value()).isEqualTo(200);
        assertThat(patchResp.getBody().getStatus().name()).isEqualTo("IN_PROGRESS");

        // DB truth: task status updated
        TaskEntity saved = taskRepo.findById(taskId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        // DB truth: note inserted
        long notesAfter = noteRepo.count();
        assertThat(notesAfter).isEqualTo(notesBefore + 1);

        // Now rollback proof: try PATCH with simulated failure
        long notesBeforeFail = noteRepo.count();
        TaskStatus statusBeforeFail = taskRepo.findById(taskId).orElseThrow().getStatus();

        try {
            rest.exchange(
                    baseUrl + "/tasks/" + taskId,
                    HttpMethod.PATCH,
                    new HttpEntity<>(new TaskStatusUpdateRequest("DONE", true)),
                    String.class
            );
        } catch (HttpServerErrorException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(500);
        }

        // After failure: status must NOT change, notes must NOT increase
        TaskStatus statusAfterFail = taskRepo.findById(taskId).orElseThrow().getStatus();
        long notesAfterFail = noteRepo.count();

        assertThat(statusAfterFail).isEqualTo(statusBeforeFail);
        assertThat(notesAfterFail).isEqualTo(notesBeforeFail);
    }
}