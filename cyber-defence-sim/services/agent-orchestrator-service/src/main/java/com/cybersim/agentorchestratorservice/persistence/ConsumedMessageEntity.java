package com.cybersim.agentorchestratorservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consumed_messages")
class ConsumedMessageEntity {
    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected ConsumedMessageEntity() {
    }

    ConsumedMessageEntity(UUID messageId, String eventType, Instant consumedAt) {
        this.messageId = messageId;
        this.eventType = eventType;
        this.consumedAt = consumedAt;
    }
}
