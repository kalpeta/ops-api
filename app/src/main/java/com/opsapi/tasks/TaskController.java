package com.opsapi.tasks;

import com.opsapi.tasks.dto.TaskCreateRequest;
import com.opsapi.tasks.dto.TaskListResponse;
import com.opsapi.tasks.dto.TaskResponse;
import com.opsapi.tasks.dto.TaskStatusUpdateRequest;
import com.opsapi.tasks.dto.TaskSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping("/customers/{customerId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@PathVariable UUID customerId, @Valid @RequestBody TaskCreateRequest req) {
        return service.createTaskForCustomer(customerId, req);
    }

    @GetMapping("/customers/{customerId}/tasks")
    public TaskListResponse listTasks(@PathVariable UUID customerId) {
        return service.listTasksForCustomer(customerId);
    }

    @GetMapping("/customers/{customerId}/tasks/summary")
    public TaskSummaryResponse summary(@PathVariable UUID customerId) {
        return service.listTaskSummaries(customerId);
    }

    @PatchMapping("/tasks/{taskId}")
    public TaskResponse updateStatus(@PathVariable UUID taskId, @Valid @RequestBody TaskStatusUpdateRequest req) {
        return service.updateStatus(taskId, req);
    }
}