package com.cybersim.simulationorchestratorservice.model;

import com.cybersim.shared.dto.RoundCompletionRequest;
import com.cybersim.shared.dto.SimulationRoundResponse;

import java.time.Instant;
import java.util.UUID;

public record SimulationRoundRecord(
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
    public static SimulationRoundRecord create(UUID simulationId, int roundNumber, Instant now) {
        return new SimulationRoundRecord(UUID.randomUUID(), simulationId, roundNumber, "CREATED", now, null,
                0, 0, 0, 0, 0);
    }

    public SimulationRoundRecord advance() {
        String nextStatus = switch (status) {
            case "CREATED" -> "RED_TEAM_RUNNING";
            case "RED_TEAM_RUNNING" -> "DETECTION_RUNNING";
            case "DETECTION_RUNNING" -> "BLUE_TEAM_RUNNING";
            case "BLUE_TEAM_RUNNING" -> "VERIFYING";
            case "VERIFYING" -> "SCORING";
            default -> throw new IllegalStateException("Round cannot advance from " + status);
        };
        return new SimulationRoundRecord(id, simulationId, roundNumber, nextStatus, startedAt, endedAt,
                newFindingsCount, remediatedFindingsCount, verifiedFixesCount, riskScoreBefore, riskScoreAfter);
    }

    public SimulationRoundRecord complete(RoundCompletionRequest request, Instant now) {
        if (!"SCORING".equals(status)) {
            throw new IllegalStateException("Only a scoring round can be completed");
        }
        return new SimulationRoundRecord(id, simulationId, roundNumber, "COMPLETED", startedAt, now,
                request.newFindingsCount(), request.remediatedFindingsCount(), request.verifiedFixesCount(),
                request.riskScoreBefore(), request.riskScoreAfter());
    }

    public SimulationRoundRecord fail(Instant now) {
        return new SimulationRoundRecord(id, simulationId, roundNumber, "FAILED", startedAt, now,
                newFindingsCount, remediatedFindingsCount, verifiedFixesCount, riskScoreBefore, riskScoreAfter);
    }

    public SimulationRoundResponse toResponse() {
        return new SimulationRoundResponse(id, simulationId, roundNumber, status, startedAt, endedAt,
                newFindingsCount, remediatedFindingsCount, verifiedFixesCount, riskScoreBefore, riskScoreAfter);
    }
}
