package com.opsapi.customers;

import com.opsapi.common.CorrelationIdFilter;
import com.opsapi.dependency.DependencyStubClient;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomerDependencyService {

    private final CustomerRepository customerRepository;
    private final DependencyStubClient stubClient;

    private final String configuredBaseUrl;

    public CustomerDependencyService(
            CustomerRepository customerRepository,
            DependencyStubClient stubClient,
            @Value("${ops.dependency.base-url:}") String configuredBaseUrl
    ) {
        this.customerRepository = customerRepository;
        this.stubClient = stubClient;
        this.configuredBaseUrl = configuredBaseUrl;
    }

    public ResponseEntity<Object> checkDependency(
            UUID customerId,
            String mode,
            Integer delayMs,
            HttpServletRequest request
    ) {
        boolean exists = customerRepository.existsById(customerId);
        if (!exists) {
            throw new CustomerNotFoundException(customerId);
        }

        // Prefer "same app" baseUrl inferred from the incoming request (works for tests + docker)
        String inferredBaseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();

        // If someone explicitly configured a base url, use it; otherwise use inferred
        String baseUrl = (configuredBaseUrl != null && !configuredBaseUrl.isBlank())
                ? configuredBaseUrl.trim()
                : inferredBaseUrl;

        String corr = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (corr == null || corr.isBlank()) {
            // safety fallback (should not happen because filter always sets it)
            corr = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        }

        return stubClient.callStub(baseUrl, corr, mode, delayMs);
    }
}
