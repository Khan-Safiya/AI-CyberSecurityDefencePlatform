package com.cybersim.detectionengineservice.model;

import com.cybersim.shared.dto.DetectionRuleCreateRequest;
import com.cybersim.shared.dto.DetectionRuleResponse;
import com.cybersim.shared.dto.DetectionRuleUpdateRequest;

import java.time.Instant;
import java.util.UUID;

public record DetectionRuleRecord(
        UUID id,
        String name,
        String description,
        String eventPattern,
        String severity,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static DetectionRuleRecord from(DetectionRuleCreateRequest request) {
        Instant now = Instant.now();
        return new DetectionRuleRecord(UUID.randomUUID(), request.name(), request.description(), request.eventPattern(),
                request.severity(), request.enabled(), now, now);
    }

    public DetectionRuleRecord update(DetectionRuleUpdateRequest request) {
        return new DetectionRuleRecord(id,
                request.name() == null ? name : request.name(),
                request.description() == null ? description : request.description(),
                request.eventPattern() == null ? eventPattern : request.eventPattern(),
                request.severity() == null ? severity : request.severity(),
                request.enabled() == null ? enabled : request.enabled(),
                createdAt, Instant.now());
    }

    public DetectionRuleResponse toResponse() {
        return new DetectionRuleResponse(id, name, description, eventPattern, severity, enabled, createdAt, updatedAt);
    }
}
