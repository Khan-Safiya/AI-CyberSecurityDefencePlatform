package com.cybersim.shared.dto;

import java.util.UUID;

public record SimulationRequest(
        String name,
        TargetMode mode,
        UUID targetId,
        int maxRounds,
        int maxDurationMinutes,
        int stopWhenNoNewFindingsForRounds
) {
}
