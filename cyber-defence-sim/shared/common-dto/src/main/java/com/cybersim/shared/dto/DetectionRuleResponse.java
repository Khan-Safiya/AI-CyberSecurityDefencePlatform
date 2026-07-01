package com.cybersim.shared.dto;

import java.time.Instant;
import java.util.UUID;

public record DetectionRuleResponse(
        UUID id,
        String name,
        String description,
        String eventPattern,
        String severity,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
