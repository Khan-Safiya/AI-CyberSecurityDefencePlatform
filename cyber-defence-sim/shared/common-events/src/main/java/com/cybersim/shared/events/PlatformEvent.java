package com.cybersim.shared.events;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlatformEvent(
        UUID eventId,
        @NotBlank @Size(max = 200) String eventType,
        UUID simulationId,
        UUID roundId,
        UUID targetId,
        @NotBlank @Size(max = 200) String producerService,
        @NotBlank @Size(max = 128) String correlationId,
        @NotNull Instant timestamp,
        @NotNull @Size(max = 100) Map<String, Object> payload
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
