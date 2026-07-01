package com.cybersim.shared.dto;

import java.time.Instant;
import java.util.UUID;

public record ScoreEventResponse(
        UUID id,
        UUID simulationId,
        UUID roundId,
        UUID sourceEventId,
        UUID agentId,
        String scoreType,
        String team,
        int points,
        String reason,
        boolean agentBlocked,
        Instant createdAt
) {
}
