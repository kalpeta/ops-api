package com.opsapi.tasks.dto;

import java.util.List;

public class TaskListResponse {
    private final int count;
    private final List<TaskResponse> items;

    public TaskListResponse(int count, List<TaskResponse> items) {
        this.count = count;
        this.items = items;
    }

    public int getCount() {
        return count;
    }

    public List<TaskResponse> getItems() {
        return items;
    }
}