package com.cybersim.scoringservice.model;

import com.cybersim.shared.dto.ScoreEventCreateRequest;
import com.cybersim.shared.dto.ScoreEventResponse;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ScoreEventRecord(
        UUID id,
        UUID simulationId,
        UUID roundId,
        UUID sourceEventId,
        UUID agentId,
        ScoreRule scoreType,
        String team,
        int points,
        String reason,
        boolean agentBlocked,
        Instant createdAt
) {
    public static ScoreEventRecord from(ScoreEventCreateRequest request) {
        ScoreRule rule = ScoreRule.valueOf(request.scoreType());
        return new ScoreEventRecord(UUID.randomUUID(), request.simulationId(), request.roundId(),
                request.sourceEventId(), request.agentId(), rule, rule.team(), rule.points(), rule.reason(),
                rule.blocksAgent(), Instant.now());
    }

    public boolean representsSameRequest(ScoreEventCreateRequest request) {
        return simulationId.equals(request.simulationId())
                && sourceEventId.equals(request.sourceEventId())
                && Objects.equals(roundId, request.roundId())
                && Objects.equals(agentId, request.agentId())
                && scoreType.name().equals(request.scoreType());
    }

    public ScoreEventResponse toResponse() {
        return new ScoreEventResponse(id, simulationId, roundId, sourceEventId, agentId, scoreType.name(), team,
                points, reason, agentBlocked, createdAt);
    }
}
