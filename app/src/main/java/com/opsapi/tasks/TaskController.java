package com.opsapi.tasks;

import com.opsapi.tasks.dto.TaskCreateRequest;
import com.opsapi.tasks.dto.TaskResponse;
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
}