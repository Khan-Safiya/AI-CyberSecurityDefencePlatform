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
        int maxRounds,
        int maxDurationMinutes,
        int stopWhenNoNewFindingsForRounds,
        String minimumAcceptedRiskLevel,
        boolean retestEnabled,
        int retestDelaySeconds,
        int redTeamScore,
        int blueTeamScore,
        int finalRiskScore,
        String stopReason,
        List<String> timeline,
        Instant startedAt,
        Instant endedAt
) {
}
