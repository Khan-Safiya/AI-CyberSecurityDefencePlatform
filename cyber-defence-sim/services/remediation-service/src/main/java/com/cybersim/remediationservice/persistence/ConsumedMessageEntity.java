package com.cybersim.remediationservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consumed_messages")
class ConsumedMessageEntity {
    @Id @Column(name = "message_id") private UUID messageId;
    @Column(name = "consumed_at", nullable = false) private Instant consumedAt;

    protected ConsumedMessageEntity() {
    }

    ConsumedMessageEntity(UUID messageId, Instant consumedAt) {
        this.messageId = messageId;
        this.consumedAt = consumedAt;
    }
}
