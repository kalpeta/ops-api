package com.opsapi.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsapi.common.CorrelationIdFilter;
import com.opsapi.customers.CustomerEntity;
import com.opsapi.outbox.OutboxEventEntity;
import com.opsapi.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class CustomerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CustomerEventPublisher.class);

    // Level 4: event schema versioning
    private static final int SCHEMA_VERSION = 2; // move to 1 if you want v1 only
    private static final String SOURCE = "ops-api"; // v2 optional field example

    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public CustomerEventPublisher(OutboxEventRepository outboxRepo, ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    public void publishCustomerCreated(CustomerEntity customer) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        Map<String, Object> event = new LinkedHashMap<>();
        String eventId = UUID.randomUUID().toString();

        // ---- base fields (v1) ----
        event.put("eventId", eventId);
        event.put("type", "CUSTOMER_CREATED");
        event.put("ts", Instant.now().toString());
        event.put("customerId", customer.getId().toString());
        event.put("name", customer.getName());
        event.put("email", customer.getEmail());
        event.put("correlationId", correlationId);

        // ---- Level 4: schemaVersion + optional v2 field(s) ----
        event.put("schemaVersion", SCHEMA_VERSION);

        // v2 example: add optional field(s). v1 consumers should ignore this.
        if (SCHEMA_VERSION >= 2) {
            event.put("source", SOURCE);
        }

        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEventEntity row = new OutboxEventEntity(
                    UUID.randomUUID(),
                    "CUSTOMER",
                    customer.getId(),
                    "CUSTOMER_CREATED",
                    payload,
                    correlationId
            );

            outboxRepo.save(row);

            log.info("OUTBOX_ENQUEUED eventType=CUSTOMER_CREATED aggregateId={} schemaVersion={} corr={}",
                    customer.getId(), SCHEMA_VERSION, correlationId);

        } catch (Exception e) {
            // If serialization fails, that's a real bug; fail the request (don’t silently lose events)
            throw new RuntimeException("Failed to build outbox event", e);
        }
    }
}
