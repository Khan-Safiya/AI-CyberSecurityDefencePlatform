package com.cybersim.simulationorchestratorservice.model;

import com.cybersim.shared.dto.SimulationResponse;
import com.cybersim.shared.dto.TargetMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SimulationRecord(
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
    public SimulationRecord {
        timeline = List.copyOf(timeline);
    }

    public SimulationRecord afterRound(
            int roundNumber,
            int nextRoundNumber,
            int nextRedTeamScore,
            int nextBlueTeamScore,
            int nextRiskScore,
            String terminalStatus,
            String reason,
            Instant now
    ) {
        List<String> nextTimeline = new ArrayList<>(timeline);
        nextTimeline.add("round." + roundNumber + ".completed");
        if (terminalStatus == null) {
            nextTimeline.add("round." + nextRoundNumber + ".created");
        } else {
            nextTimeline.add("simulation." + terminalStatus.toLowerCase() + ": " + reason);
        }
        return new SimulationRecord(id, name, mode, targetId, terminalStatus == null ? "RUNNING" : terminalStatus,
                terminalStatus == null ? nextRoundNumber : roundNumber, maxRounds, maxDurationMinutes,
                stopWhenNoNewFindingsForRounds, minimumAcceptedRiskLevel, retestEnabled, retestDelaySeconds,
                nextRedTeamScore, nextBlueTeamScore, nextRiskScore, reason, nextTimeline, startedAt,
                terminalStatus == null ? null : now);
    }

    public SimulationRecord manuallyStopped(Instant now) {
        List<String> nextTimeline = new ArrayList<>(timeline);
        nextTimeline.add("simulation.cancelled: MANUAL_STOP");
        return new SimulationRecord(id, name, mode, targetId, "CANCELLED", currentRound, maxRounds,
                maxDurationMinutes, stopWhenNoNewFindingsForRounds, minimumAcceptedRiskLevel, retestEnabled,
                retestDelaySeconds, redTeamScore, blueTeamScore, finalRiskScore, "MANUAL_STOP", nextTimeline,
                startedAt, now);
    }

    public SimulationResponse toResponse() {
        return new SimulationResponse(id, name, mode, targetId, status, currentRound, maxRounds, maxDurationMinutes,
                stopWhenNoNewFindingsForRounds, minimumAcceptedRiskLevel, retestEnabled, retestDelaySeconds,
                redTeamScore, blueTeamScore, finalRiskScore, stopReason, timeline, startedAt, endedAt);
    }
}
