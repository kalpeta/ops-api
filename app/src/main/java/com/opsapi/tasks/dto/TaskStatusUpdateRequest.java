package com.opsapi.tasks.dto;

import jakarta.validation.constraints.NotNull;

public class TaskStatusUpdateRequest {

    @NotNull(message = "status is required")
    private String status;

    // TEMP: for rollback test (simulate failure after task update)
    private boolean simulateFailure;

    public TaskStatusUpdateRequest() {
    }

    public TaskStatusUpdateRequest(String status, boolean simulateFailure) {
        this.status = status;
        this.simulateFailure = simulateFailure;
    }

    public String getStatus() {
        return status;
    }

    public boolean isSimulateFailure() {
        return simulateFailure;
    }
}