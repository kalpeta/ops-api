package com.opsapi.tasks;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerResponse;
import com.opsapi.notes.NoteRepository;
import com.opsapi.tasks.dto.TaskCreateRequest;
import com.opsapi.tasks.dto.TaskResponse;
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
class TaskTransactionIntegrationTest {

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

        // Ensure migrations run in tests too
        registry.add("spring.flyway.enabled", () -> true);

        // Keep schema creation owned by Flyway
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
    }

    @LocalServerPort
    int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void createTask_createsNote_andFailureRollsBackEverything() {
        String baseUrl = "http://localhost:" + port;

        // 1) Create a customer (task must belong to an existing customer)
        CustomerCreateRequest customerReq = new CustomerCreateRequest("Txn Owner", "txn-owner@example.com");
        ResponseEntity<CustomerResponse> customerResp =
                rest.postForEntity(baseUrl + "/customers", customerReq, CustomerResponse.class);

        assertThat(customerResp.getStatusCode().value()).isEqualTo(201);
        CustomerResponse customer = customerResp.getBody();
        assertThat(customer).isNotNull();
        UUID customerId = customer.getId();
        assertThat(customerId).isNotNull();

        // 2) Create task successfully -> should create BOTH task + note
        TaskCreateRequest okReq = new TaskCreateRequest("OK task", false);
        ResponseEntity<TaskResponse> okTaskResp = rest.postForEntity(
                baseUrl + "/customers/" + customerId + "/tasks",
                okReq,
                TaskResponse.class
        );

        assertThat(okTaskResp.getStatusCode().value()).isEqualTo(201);
        TaskResponse okTask = okTaskResp.getBody();
        assertThat(okTask).isNotNull();
        assertThat(okTask.getCustomerId()).isEqualTo(customerId);
        assertThat(okTask.getTitle()).isEqualTo("OK task");
        assertThat(okTask.getStatus().name()).isEqualTo("OPEN");
        UUID okTaskId = okTask.getId();
        assertThat(okTaskId).isNotNull();

        // 3) Assert note exists for the created task by calling DB through repositories
        // We use Spring-managed repos for a simple, direct DB truth check.
        // (We can't "see" notes from the HTTP response yet, by design.)
        // Autowire repositories through Spring context:
        // We'll fetch them via ApplicationContext trick: easiest is field injection, but we keep this test simple by
        // using a static holder pattern is overkill. Instead, use a nested helper with @Autowired via constructor.
        // So: we assert indirectly using counts and by querying notes via REST later in Level 3.
        //
        // For now, we validate transaction behavior strongly using counts.
        long taskCountAfterOk = getBean(TaskRepository.class).count();
        long noteCountAfterOk = getBean(NoteRepository.class).count();

        assertThat(taskCountAfterOk).isGreaterThanOrEqualTo(1);
        assertThat(noteCountAfterOk).isGreaterThanOrEqualTo(1);

        // 4) Now simulate failure AFTER task insert.
        // Transaction should roll back -> taskCount and noteCount must NOT change.
        TaskCreateRequest failReq = new TaskCreateRequest("FAIL task", true);

        long taskCountBeforeFail = getBean(TaskRepository.class).count();
        long noteCountBeforeFail = getBean(NoteRepository.class).count();

        try {
            rest.exchange(
                    baseUrl + "/customers/" + customerId + "/tasks",
                    HttpMethod.POST,
                    new HttpEntity<>(failReq),
                    String.class
            );
        } catch (HttpServerErrorException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(500);
        }

        long taskCountAfterFail = getBean(TaskRepository.class).count();
        long noteCountAfterFail = getBean(NoteRepository.class).count();

        assertThat(taskCountAfterFail).isEqualTo(taskCountBeforeFail);
        assertThat(noteCountAfterFail).isEqualTo(noteCountBeforeFail);
    }

    // ---------
    // Tiny helper: pull Spring beans without field injection (keeps file standalone).
    // ---------
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext ctx;

    private <T> T getBean(Class<T> type) {
        return ctx.getBean(type);
    }
}