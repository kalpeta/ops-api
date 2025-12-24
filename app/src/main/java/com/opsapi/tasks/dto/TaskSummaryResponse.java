package com.opsapi.tasks.dto;

import java.util.List;

public class TaskSummaryResponse {
    private final int count;
    private final List<TaskSummaryItem> items;

    public TaskSummaryResponse(int count, List<TaskSummaryItem> items) {
        this.count = count;
        this.items = items;
    }

    public int getCount() {
        return count;
    }

    public List<TaskSummaryItem> getItems() {
        return items;
    }
}