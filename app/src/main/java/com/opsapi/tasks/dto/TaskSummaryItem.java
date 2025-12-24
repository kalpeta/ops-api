package com.opsapi.tasks.dto;

import com.opsapi.tasks.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public class TaskSummaryItem {
    private final UUID id;
    private final UUID customerId;
    private final String title;
    private final TaskStatus status;
    private final Instant updatedAt;

    private final String latestNoteSnippet;
    private final Instant latestNoteCreatedAt;

    public TaskSummaryItem(
            UUID id,
            UUID customerId,
            String title,
            TaskStatus status,
            Instant updatedAt,
            String latestNoteSnippet,
            Instant latestNoteCreatedAt
    ) {
        this.id = id;
        this.customerId = customerId;
        this.title = title;
        this.status = status;
        this.updatedAt = updatedAt;
        this.latestNoteSnippet = latestNoteSnippet;
        this.latestNoteCreatedAt = latestNoteCreatedAt;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getLatestNoteSnippet() {
        return latestNoteSnippet;
    }

    public Instant getLatestNoteCreatedAt() {
        return latestNoteCreatedAt;
    }
}