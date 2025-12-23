package com.opsapi.tasks.dto;

import jakarta.validation.constraints.NotBlank;

public class TaskCreateRequest {

    @NotBlank(message = "title is required")
    private String title;

    // TEMP (for rollback test): if true, service will throw after inserting task
    private boolean simulateFailure;

    public TaskCreateRequest() {
    }

    public TaskCreateRequest(String title, boolean simulateFailure) {
        this.title = title;
        this.simulateFailure = simulateFailure;
    }

    public String getTitle() {
        return title;
    }

    public boolean isSimulateFailure() {
        return simulateFailure;
    }
}