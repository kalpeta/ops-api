package com.opsapi.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class OpsMetrics {

    private final MeterRegistry registry;

    public OpsMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Mental model:
     * - "name" = the bucket we store timings in
     * - We run the business code and measure how long it took
     * - Then Actuator can show COUNT / TOTAL / MAX for that name
     */
    public <T> T time(String name, Supplier<T> action) {
        long startNs = System.nanoTime();
        try {
            return action.get();
        } finally {
            long durationNs = System.nanoTime() - startNs;
            Timer.builder(name).register(registry).record(durationNs, TimeUnit.NANOSECONDS);
        }
    }
}