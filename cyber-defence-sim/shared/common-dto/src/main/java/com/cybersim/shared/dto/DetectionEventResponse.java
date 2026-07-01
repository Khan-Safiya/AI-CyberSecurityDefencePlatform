package com.cybersim.shared.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DetectionEventResponse(
        UUID id,
        UUID simulationId,
        UUID roundId,
        UUID targetId,
        String source,
        String eventType,
        String severity,
        String message,
        UUID relatedActionId,
        UUID relatedVulnerabilityId,
        Map<String, String> metadata,
        Instant createdAt
) {
}
