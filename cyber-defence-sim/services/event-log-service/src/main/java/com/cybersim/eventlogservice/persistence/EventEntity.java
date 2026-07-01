package com.cybersim.eventlogservice.persistence;

import com.cybersim.shared.events.PlatformEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
class EventEntity {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;

    @Column(name = "simulation_id")
    private UUID simulationId;

    @Column(name = "round_id")
    private UUID roundId;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "producer_service", nullable = false, length = 200)
    private String producerService;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected EventEntity() {
    }

    private EventEntity(PlatformEvent event, String payloadJson, Instant recordedAt) {
        eventId = event.eventId();
        eventType = event.eventType();
        simulationId = event.simulationId();
        roundId = event.roundId();
        targetId = event.targetId();
        producerService = event.producerService();
        correlationId = event.correlationId();
        eventTimestamp = event.timestamp();
        this.payloadJson = payloadJson;
        this.recordedAt = recordedAt;
    }

    static EventEntity from(PlatformEvent event, String payloadJson, Instant recordedAt) {
        return new EventEntity(event, payloadJson, recordedAt);
    }

    UUID eventId() {
        return eventId;
    }

    String eventType() {
        return eventType;
    }

    UUID simulationId() {
        return simulationId;
    }

    UUID roundId() {
        return roundId;
    }

    UUID targetId() {
        return targetId;
    }

    String producerService() {
        return producerService;
    }

    String correlationId() {
        return correlationId;
    }

    Instant eventTimestamp() {
        return eventTimestamp;
    }

    String payloadJson() {
        return payloadJson;
    }
}
