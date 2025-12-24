package com.opsapi.notes.dto;

import java.time.Instant;
import java.util.UUID;

public class NoteResponse {
    private final UUID id;
    private final UUID taskId;
    private final String body;
    private final Instant createdAt;

    public NoteResponse(UUID id, UUID taskId, String body, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}