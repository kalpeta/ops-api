package com.opsapi.tasks.dto;

import com.opsapi.tasks.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public class TaskResponse {
    private final UUID id;
    private final UUID customerId;
    private final String title;
    private final TaskStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public TaskResponse(UUID id, UUID customerId, String title, TaskStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.title = title;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getTitle() {
        return title;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}