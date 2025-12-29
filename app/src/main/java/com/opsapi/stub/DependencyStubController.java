package com.opsapi.stub;

import com.opsapi.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/_stub")
@Profile("local") // local-only: this controller only exists when profile=local
public class DependencyStubController {

    private static final Logger log = LoggerFactory.getLogger(DependencyStubController.class);

    /**
     * Fake dependency endpoint.
     *
     * Examples:
     *  - /_stub/dependency?mode=ok
     *  - /_stub/dependency?mode=fail
     *  - /_stub/dependency?mode=slow&delayMs=1500
     */
    @GetMapping("/dependency")
    public ResponseEntity<?> dependency(
            @RequestParam(defaultValue = "ok") String mode,
            @RequestParam(defaultValue = "1500") long delayMs,
            HttpServletRequest req
    ) {
        String normalizedMode = mode == null ? "ok" : mode.trim().toLowerCase();

        if (delayMs < 0 || delayMs > 30_000) {
            // This will be formatted nicely by your ApiExceptionHandler (BAD_REQUEST)
            throw new IllegalArgumentException("delayMs must be between 0 and 30000");
        }

        log.info("STUB_DEPENDENCY mode={} delayMs={}", normalizedMode, delayMs);

        switch (normalizedMode) {
            case "ok" -> {
                return ResponseEntity.ok(Map.of(
                        "stub", "dependency",
                        "mode", "ok"
                ));
            }

            case "fail" -> {
                // Return the SAME error JSON shape as the rest of your API
                ApiErrorResponse body = new ApiErrorResponse(
                        Instant.now(),
                        503,
                        "SERVICE_UNAVAILABLE",
                        "Stub forced failure (mode=fail)",
                        req.getRequestURI(),
                        List.of()
                );
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
            }

            case "slow" -> {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    ApiErrorResponse body = new ApiErrorResponse(
                            Instant.now(),
                            500,
                            "INTERNAL_ERROR",
                            "Stub sleep interrupted",
                            req.getRequestURI(),
                            List.of()
                    );
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
                }

                return ResponseEntity.ok(Map.of(
                        "stub", "dependency",
                        "mode", "slow",
                        "delayMs", delayMs
                ));
            }

            default -> throw new IllegalArgumentException("mode must be one of: ok, fail, slow");
        }
    }
}
