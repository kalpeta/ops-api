package com.opsapi.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String incoming = request.getHeader(HEADER_NAME);
        String correlationId = (incoming != null && !incoming.isBlank())
                ? incoming.trim()
                : UUID.randomUUID().toString();

        // Put into MDC so ALL logs during this request can print it
        MDC.put(MDC_KEY, correlationId);

        // Send it back so callers can log/trace too
        response.setHeader(HEADER_NAME, correlationId);

        // Build a readable "path" (include query string only if present)
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String path = (query == null || query.isBlank()) ? uri : (uri + "?" + query);

        long startNs = System.nanoTime();

        // 1) REQUEST_START
        log.info("REQUEST_START {} {}", method, path);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 2) REQUEST_END (status + latency)
            int status = response.getStatus();
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;

            log.info("REQUEST_END {} {} status={} latency_ms={}", method, path, status, latencyMs);

            // IMPORTANT: remove to avoid leaking into other requests/threads
            MDC.remove(MDC_KEY);
        }
    }
}