package com.opsapi.notes.dto;

import jakarta.validation.constraints.NotBlank;

public class NoteCreateRequest {

    @NotBlank(message = "body is required")
    private String body;

    public NoteCreateRequest() {
    }

    public NoteCreateRequest(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }
}