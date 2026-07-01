package com.cybersim.detectionengineservice.model;

import com.cybersim.shared.dto.DetectionEventCreateRequest;
import com.cybersim.shared.dto.DetectionEventResponse;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DetectionEventRecord(
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
    public DetectionEventRecord {
        metadata = Map.copyOf(metadata);
    }

    public static DetectionEventRecord from(DetectionEventCreateRequest request) {
        return from(request, UUID.randomUUID());
    }

    public static DetectionEventRecord from(DetectionEventCreateRequest request, UUID id) {
        return new DetectionEventRecord(id, request.simulationId(), request.roundId(), request.targetId(),
                request.source(), request.eventType(), request.severity(), request.message(), request.relatedActionId(),
                request.relatedVulnerabilityId(), request.metadata(), Instant.now());
    }

    public boolean sameEvent(DetectionEventCreateRequest request) {
        return simulationId.equals(request.simulationId())
                && java.util.Objects.equals(roundId, request.roundId())
                && targetId.equals(request.targetId())
                && source.equals(request.source())
                && eventType.equals(request.eventType())
                && severity.equals(request.severity())
                && message.equals(request.message())
                && java.util.Objects.equals(relatedActionId, request.relatedActionId())
                && java.util.Objects.equals(relatedVulnerabilityId, request.relatedVulnerabilityId())
                && metadata.equals(request.metadata());
    }

    public DetectionEventResponse toResponse() {
        return new DetectionEventResponse(id, simulationId, roundId, targetId, source, eventType, severity, message,
                relatedActionId, relatedVulnerabilityId, metadata, createdAt);
    }
}
