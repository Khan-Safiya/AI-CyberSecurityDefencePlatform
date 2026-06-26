package com.cybersim.shared.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlatformEvent(
        UUID eventId,
        String eventType,
        UUID simulationId,
        UUID roundId,
        UUID targetId,
        String producerService,
        String correlationId,
        Instant timestamp,
        Map<String, Object> payload
) {
    public static PlatformEvent of(String eventType, UUID simulationId, UUID targetId, String producerService) {
        return new PlatformEvent(
                UUID.randomUUID(),
                eventType,
                simulationId,
                null,
                targetId,
                producerService,
                UUID.randomUUID().toString(),
                Instant.now(),
                Map.of()
        );
    }
}
