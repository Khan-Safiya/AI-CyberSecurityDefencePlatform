package com.cybersim.shared.dto;

public record RoundCompletionResponse(
        SimulationResponse simulation,
        SimulationRoundResponse completedRound,
        SimulationRoundResponse nextRound,
        String stopReason
) {
}
