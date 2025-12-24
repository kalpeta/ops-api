package com.opsapi.notes.dto;

import java.util.List;

public class NoteListResponse {
    private final int count;
    private final List<NoteResponse> items;

    public NoteListResponse(int count, List<NoteResponse> items) {
        this.count = count;
        this.items = items;
    }

    public int getCount() {
        return count;
    }

    public List<NoteResponse> getItems() {
        return items;
    }
}