package com.cybersim.shared.dto;

import java.time.Instant;
import java.util.UUID;

public record VerificationResponse(
        UUID id,
        UUID simulationId,
        UUID roundId,
        UUID vulnerabilityId,
        UUID remediationId,
        UUID targetId,
        String status,
        String evidenceSummary,
        Instant verifiedAt
) {
}
