package com.opsapi.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsapi.common.CorrelationIdFilter;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DependencyStubClient {

    private static final Logger log = LoggerFactory.getLogger(DependencyStubClient.class);

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    private final int timeoutMs;
    private final int maxAttempts;
    private final int backoffMs;

    private final CircuitBreaker cb;
    private final Bulkhead bulkhead;

    public DependencyStubClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            CircuitBreakerRegistry circuitBreakerRegistry,
            BulkheadRegistry bulkheadRegistry,
            @Value("${ops.dependency.timeout-ms:800}") int timeoutMs,
            @Value("${ops.dependency.retry.max-attempts:3}") int maxAttempts,
            @Value("${ops.dependency.retry.backoff-ms:100}") int backoffMs
    ) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.timeoutMs = timeoutMs;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = Math.max(0, backoffMs);

        this.cb = circuitBreakerRegistry.circuitBreaker("dependencyStub");
        this.bulkhead = bulkheadRegistry.bulkhead("dependencyStub");

        this.cb.getEventPublisher()
                .onStateTransition(e ->
                        log.warn("CB_TRANSITION name={} {}", cb.getName(), e.getStateTransition())
                );

        this.bulkhead.getEventPublisher()
                .onCallRejected(e ->
                        log.warn("BULKHEAD_REJECT name={} max_concurrent_reached", bulkhead.getName())
                );
    }

    /**
     * Slice 2: Fallback response when CircuitBreaker is OPEN.
     * - Bulkhead limits concurrency (429 if saturated)
     * - CB open -> return stable fallback JSON (HTTP 200, degraded=true)
     * - Upstream failures/timeouts still propagate as-is (but count as failures for CB)
     */
    public ResponseEntity<Object> callStub(String baseUrl, String correlationId, String mode, Integer delayMs) {
        String urlForLogs = baseUrl + "/_stub/dependency";

        try {
            // Bulkhead OUTSIDE CB so bulkhead saturation doesn't trip CB
            return bulkhead.executeSupplier(() -> {
                try {
                    return cb.executeSupplier(() -> callStubWithRetryAndTimeout(baseUrl, correlationId, mode, delayMs));

                } catch (CallNotPermittedException ex) {
                    // === FALLBACK (Slice 2) ===
                    log.warn("CB_OPEN_FALLBACK name={} url={} corr={} (returning degraded response)",
                            cb.getName(), urlForLogs, correlationId);

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("timestamp", Instant.now());
                    body.put("degraded", true);
                    body.put("stub", "dependency");
                    body.put("mode", "fallback");
                    body.put("reason", "circuit_open");
                    body.put("message", "Dependency is temporarily unavailable (circuit open). Returning fallback.");
                    body.put("upstream", urlForLogs);

                    // 200 on purpose: API still responds deterministically with a safe, parseable shape
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body);

                } catch (UpstreamFailure ex) {
                    // Preserve upstream response while still counting as failure in CB
                    return ex.response;
                }
            });

        } catch (BulkheadFullException ex) {
            log.warn("BULKHEAD_FULL name={} url={} corr={} (rejecting request)",
                    bulkhead.getName(), urlForLogs, correlationId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", Instant.now());
            body.put("status", 429);
            body.put("error", "BULKHEAD_FULL");
            body.put("message", "Too many concurrent dependency calls. Please retry.");
            body.put("upstream", urlForLogs);

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        }
    }

    private ResponseEntity<Object> callStubWithRetryAndTimeout(String baseUrl, String correlationId, String mode, Integer delayMs) {
        String urlForLogs = baseUrl + "/_stub/dependency";

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(timeoutMs));
        rf.setReadTimeout(Duration.ofMillis(timeoutMs));

        RestClient client = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(rf)
                .build();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startNs = System.nanoTime();
            log.info("OUTBOUND_ATTEMPT {}/{} GET {} mode={} delayMs={} timeoutMs={}",
                    attempt, maxAttempts, urlForLogs, mode, delayMs, timeoutMs);

            try {
                ResponseEntity<Object> resp = client.get()
                        .uri(uriBuilder -> {
                            var b = uriBuilder.path("/_stub/dependency").queryParam("mode", mode);
                            if (delayMs != null) b = b.queryParam("delayMs", delayMs);
                            return b.build();
                        })
                        .header(CorrelationIdFilter.HEADER_NAME, correlationId)
                        .retrieve()
                        .toEntity(Object.class);

                long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
                log.info("OUTBOUND_END GET {} attempt={} status={} latency_ms={}",
                        urlForLogs, attempt, resp.getStatusCode().value(), latencyMs);

                if (resp.getStatusCode().is5xxServerError()) {
                    throw new UpstreamFailure(resp);
                }

                return resp;

            } catch (RestClientResponseException ex) {
                long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
                int status = ex.getRawStatusCode();
                log.info("OUTBOUND_END GET {} attempt={} status={} latency_ms={}",
                        urlForLogs, attempt, status, latencyMs);

                boolean retryableStatus = status >= 500;
                boolean hasRetryLeft = attempt < maxAttempts;

                if (retryableStatus && hasRetryLeft) {
                    log.info("OUTBOUND_RETRY reason=status={} next_attempt={}/{}", status, attempt + 1, maxAttempts);
                    sleepBackoff(attempt);
                    continue;
                }

                ResponseEntity<Object> finalResp = responseFromException(ex);

                if (finalResp.getStatusCode().is5xxServerError()) {
                    throw new UpstreamFailure(finalResp);
                }

                return finalResp;

            } catch (ResourceAccessException ex) {
                long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
                boolean hasRetryLeft = attempt < maxAttempts;

                String cause = (ex.getCause() != null) ? ex.getCause().getClass().getSimpleName() : "ResourceAccessException";
                log.info("OUTBOUND_END GET {} attempt={} status=TIMEOUT_OR_IO cause={} latency_ms={}",
                        urlForLogs, attempt, cause, latencyMs);

                if (hasRetryLeft) {
                    log.info("OUTBOUND_RETRY reason={} next_attempt={}/{}", cause, attempt + 1, maxAttempts);
                    sleepBackoff(attempt);
                    continue;
                }

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("timestamp", Instant.now());
                body.put("status", 504);
                body.put("error", "GATEWAY_TIMEOUT");
                body.put("message", "Dependency call timed out after " + timeoutMs + "ms (attempts=" + maxAttempts + ")");
                body.put("upstream", urlForLogs);

                ResponseEntity<Object> finalResp = ResponseEntity
                        .status(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body);

                throw new UpstreamFailure(finalResp);
            }
        }

        ResponseEntity<Object> resp = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        throw new UpstreamFailure(resp);
    }

    private ResponseEntity<Object> responseFromException(RestClientResponseException ex) {
        Object body = null;
        String raw = ex.getResponseBodyAsString();
        if (raw != null && !raw.isBlank()) {
            try {
                body = objectMapper.readValue(raw, Object.class);
            } catch (Exception ignored) {
                body = raw;
            }
        }

        MediaType ct = ex.getResponseHeaders() != null && ex.getResponseHeaders().getContentType() != null
                ? ex.getResponseHeaders().getContentType()
                : MediaType.APPLICATION_JSON;

        return ResponseEntity
                .status(ex.getStatusCode())
                .contentType(ct)
                .body(body);
    }

    private void sleepBackoff(int attempt) {
        if (backoffMs <= 0) return;
        long sleep = (long) backoffMs * attempt;
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class UpstreamFailure extends RuntimeException {
        private final ResponseEntity<Object> response;

        private UpstreamFailure(ResponseEntity<Object> response) {
            super("Upstream failure: " + response.getStatusCode());
            this.response = response;
        }
    }
}
