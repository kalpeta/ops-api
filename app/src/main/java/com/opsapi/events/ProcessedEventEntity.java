package com.opsapi.events;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {}

    public ProcessedEventEntity(String eventId, String eventType, String correlationId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
}
