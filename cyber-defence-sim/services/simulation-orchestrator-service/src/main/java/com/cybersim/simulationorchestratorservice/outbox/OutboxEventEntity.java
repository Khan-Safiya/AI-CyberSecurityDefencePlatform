package com.cybersim.simulationorchestratorservice.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity {
    @Id
    private UUID id;
    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;
    @Column(name = "routing_key", nullable = false, length = 200)
    private String routingKey;
    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;
    @Column(name = "round_id")
    private UUID roundId;
    @Column(name = "payload_json", nullable = false, length = 8000)
    private String payloadJson;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "last_error", length = 500)
    private String lastError;

    protected OutboxEventEntity() {
    }

    private OutboxEventEntity(OutboxEventRecord event) {
        id = event.id();
        eventType = event.eventType();
        routingKey = event.routingKey();
        simulationId = event.simulationId();
        roundId = event.roundId();
        payloadJson = event.payloadJson();
        status = event.status();
        attempts = event.attempts();
        nextAttemptAt = event.nextAttemptAt();
        createdAt = event.createdAt();
        publishedAt = event.publishedAt();
        lastError = event.lastError();
    }

    static OutboxEventEntity from(OutboxEventRecord event) {
        return new OutboxEventEntity(event);
    }

    OutboxEventRecord toRecord() {
        return new OutboxEventRecord(id, eventType, routingKey, simulationId, roundId, payloadJson, status, attempts,
                nextAttemptAt, createdAt, publishedAt, lastError);
    }
}
