package com.opsapi.common;

import com.opsapi.customers.CustomerNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest req
    ) {
        List<FieldValidationError> fieldErrors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.add(new FieldValidationError(fe.getField(), fe.getDefaultMessage()))
        );

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                400,
                "VALIDATION_ERROR",
                "Request validation failed",
                req.getRequestURI(),
                fieldErrors
        );

        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleBadJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest req
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                400,
                "BAD_REQUEST",
                "Malformed JSON request body",
                req.getRequestURI(),
                List.of()
        );

        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleNotFound(
            CustomerNotFoundException ex,
            HttpServletRequest req
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                404,
                "NOT_FOUND",
                ex.getMessage(),
                req.getRequestURI(),
                List.of()
        );

        return org.springframework.http.ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleConflict(
            DataIntegrityViolationException ex,
            HttpServletRequest req
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                409,
                "CONFLICT",
                "Database constraint violated (possible duplicate email)",
                req.getRequestURI(),
                List.of()
        );

        return org.springframework.http.ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest req
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                400,
                "BAD_REQUEST",
                ex.getMessage(),
                req.getRequestURI(),
                List.of()
        );

        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest req
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                500,
                "INTERNAL_ERROR",
                "Unexpected server error",
                req.getRequestURI(),
                List.of()
        );

        return org.springframework.http.ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}