package com.opsapi.customers;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerListResponse;
import com.opsapi.customers.dto.CustomerResponse;
import com.opsapi.customers.dto.CustomerUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService service;
    private final CustomerDependencyService dependencyService;

    public CustomerController(CustomerService service, CustomerDependencyService dependencyService) {
        this.service = service;
        this.dependencyService = dependencyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerCreateRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping
    public CustomerListResponse list(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }

        return service.list(limit, offset);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerUpdateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/{id}/dependency-check")
    public ResponseEntity<Object> dependencyCheck(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "ok") String mode,
            @RequestParam(required = false) Integer delayMs,
            HttpServletRequest request
    ) {
        return dependencyService.checkDependency(id, mode, delayMs, request);
    }
}
