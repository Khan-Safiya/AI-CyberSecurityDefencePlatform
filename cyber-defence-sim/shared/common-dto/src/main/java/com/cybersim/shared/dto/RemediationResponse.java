package com.cybersim.shared.dto;

import java.time.Instant;
import java.util.UUID;

public record RemediationResponse(
        UUID id,
        UUID simulationId,
        UUID roundId,
        UUID vulnerabilityId,
        UUID detectionId,
        UUID agentId,
        UUID targetId,
        String remediationType,
        String patchSummary,
        String status,
        String outcomeSummary,
        Instant createdAt,
        Instant updatedAt,
        Instant approvedAt,
        Instant appliedAt,
        Instant verifiedAt,
        Instant rolledBackAt
) {
}
