package com.opsapi.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CustomerEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CustomerEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedRepo;

    public CustomerEventsConsumer(ObjectMapper objectMapper, ProcessedEventRepository processedRepo) {
        this.objectMapper = objectMapper;
        this.processedRepo = processedRepo;
    }

    @KafkaListener(topics = "customer-events")
    @Transactional
    public void onMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String eventId = text(root, "eventId");
            String type = text(root, "type");
            String corr = text(root, "correlationId");

            // Level 4: schema versioning (default to v1 if missing)
            int schemaVersion = intOrDefault(root, "schemaVersion", 1);

            if (eventId == null || eventId.isBlank()) {
                log.warn("EVENT_BAD payload_missing_eventId payload={}", payload);
                return;
            }

            try {
                processedRepo.save(new ProcessedEventEntity(eventId, (type == null ? "UNKNOWN" : type), corr));
            } catch (DataIntegrityViolationException dup) {
                log.info("EVENT_DUPLICATE_IGNORED eventId={} type={} schemaVersion={} corr={}",
                        eventId, type, schemaVersion, corr);
                return;
            }

            // ---- Version-aware handling (for now: just log the parsed fields) ----
            if (schemaVersion == 1) {
                // v1 has no "source"
                log.info("EVENT_PROCESSED topic=customer-events eventId={} type={} schemaVersion=1 corr={} payload={}",
                        eventId, type, corr, payload);

            } else if (schemaVersion == 2) {
                // v2 optional field example
                String source = text(root, "source"); // optional
                log.info("EVENT_PROCESSED topic=customer-events eventId={} type={} schemaVersion=2 corr={} source={} payload={}",
                        eventId, type, corr, source, payload);

            } else {
                // Unknown future version: don't crash; log + proceed conservatively
                log.warn("EVENT_UNKNOWN_VERSION eventId={} type={} schemaVersion={} corr={} payload={}",
                        eventId, type, schemaVersion, corr, payload);
            }

        } catch (Exception e) {
            log.warn("EVENT_PARSE_FAILED err={} payload={}", e.toString(), payload);
        }
    }

    private static String text(JsonNode root, String field) {
        return root.hasNonNull(field) ? root.get(field).asText() : null;
    }

    private static int intOrDefault(JsonNode root, String field, int defaultValue) {
        if (!root.has(field) || root.get(field).isNull()) return defaultValue;
        JsonNode n = root.get(field);
        if (n.isInt()) return n.asInt();
        if (n.isTextual()) {
            try {
                return Integer.parseInt(n.asText());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
