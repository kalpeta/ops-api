package com.opsapi.customers.dto;

import java.time.Instant;
import java.util.UUID;

public class CustomerResponse {
    private UUID id;
    private String name;
    private String email;
    private Instant createdAt;
    private Instant updatedAt;

    public CustomerResponse(UUID id, String name, String email, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}