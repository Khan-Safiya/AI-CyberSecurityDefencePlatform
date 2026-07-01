package com.cybersim.simulationorchestratorservice.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventRecord(
        UUID id,
        String eventType,
        String routingKey,
        UUID simulationId,
        UUID roundId,
        String payloadJson,
        String status,
        int attempts,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant publishedAt,
        String lastError
) {
    public static OutboxEventRecord pending(
            String eventType,
            String routingKey,
            UUID simulationId,
            UUID roundId,
            String payloadJson,
            Instant now
    ) {
        return new OutboxEventRecord(UUID.randomUUID(), eventType, routingKey, simulationId, roundId, payloadJson,
                "PENDING", 0, now, now, null, null);
    }

    public OutboxEventRecord published(Instant now) {
        return new OutboxEventRecord(id, eventType, routingKey, simulationId, roundId, payloadJson, "PUBLISHED",
                attempts + 1, nextAttemptAt, createdAt, now, null);
    }

    public OutboxEventRecord failed(Instant now, String errorType) {
        int nextAttempts = attempts + 1;
        long delaySeconds = Math.min(300L, 1L << Math.min(nextAttempts, 8));
        return new OutboxEventRecord(id, eventType, routingKey, simulationId, roundId, payloadJson, "FAILED",
                nextAttempts, now.plusSeconds(delaySeconds), createdAt, publishedAt, errorType);
    }
}
