package com.cybersim.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SimulationResponse(
        UUID id,
        String name,
        TargetMode mode,
        UUID targetId,
        String status,
        int currentRound,
        int redTeamScore,
        int blueTeamScore,
        int finalRiskScore,
        List<String> timeline,
        Instant startedAt,
        Instant endedAt
) {
}
