package com.opsapi.common;

import java.time.Instant;
import java.util.List;

public class ApiErrorResponse {
    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final List<FieldValidationError> fieldErrors;

    public ApiErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            List<FieldValidationError> fieldErrors
    ) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public List<FieldValidationError> getFieldErrors() {
        return fieldErrors;
    }
}