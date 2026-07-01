package com.cybersim.shared.dto;

import java.time.Instant;
import java.util.UUID;

public record SimulationRoundResponse(
        UUID id,
        UUID simulationId,
        int roundNumber,
        String status,
        Instant startedAt,
        Instant endedAt,
        int newFindingsCount,
        int remediatedFindingsCount,
        int verifiedFixesCount,
        int riskScoreBefore,
        int riskScoreAfter
) {
}
