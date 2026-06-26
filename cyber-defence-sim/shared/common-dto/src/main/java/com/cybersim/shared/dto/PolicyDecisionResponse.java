package com.cybersim.shared.dto;

import java.time.Instant;
import java.util.UUID;

public record PolicyDecisionResponse(
        UUID decisionId,
        UUID simulationId,
        UUID targetId,
        UUID agentId,
        String actionType,
        boolean allowed,
        String reason,
        Instant evaluatedAt,
        String policyVersion
) {
}
