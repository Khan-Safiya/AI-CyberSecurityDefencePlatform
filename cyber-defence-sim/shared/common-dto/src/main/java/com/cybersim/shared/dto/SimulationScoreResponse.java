package com.cybersim.shared.dto;

import java.util.List;
import java.util.UUID;

public record SimulationScoreResponse(
        UUID simulationId,
        long redTeamScore,
        long blueTeamScore,
        int finalRiskScore,
        int eventCount,
        List<UUID> blockedAgentIds
) {
    public SimulationScoreResponse {
        blockedAgentIds = List.copyOf(blockedAgentIds);
    }
}
