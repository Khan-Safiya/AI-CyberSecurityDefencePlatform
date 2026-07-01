package com.cybersim.scoringservice.persistence;

import com.cybersim.scoringservice.model.ScoreEventRecord;
import com.cybersim.scoringservice.model.ScoreRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "score_events")
class ScoreEventEntity {
    @Id
    private UUID id;
    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;
    @Column(name = "round_id")
    private UUID roundId;
    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;
    @Column(name = "agent_id")
    private UUID agentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "score_type", nullable = false, length = 60)
    private ScoreRule scoreType;
    @Column(nullable = false, length = 10)
    private String team;
    @Column(nullable = false)
    private int points;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "agent_blocked", nullable = false)
    private boolean agentBlocked;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ScoreEventEntity() {
    }

    private ScoreEventEntity(ScoreEventRecord event) {
        id = event.id();
        simulationId = event.simulationId();
        roundId = event.roundId();
        sourceEventId = event.sourceEventId();
        agentId = event.agentId();
        scoreType = event.scoreType();
        team = event.team();
        points = event.points();
        reason = event.reason();
        agentBlocked = event.agentBlocked();
        createdAt = event.createdAt();
    }

    static ScoreEventEntity from(ScoreEventRecord event) {
        return new ScoreEventEntity(event);
    }

    ScoreEventRecord toRecord() {
        return new ScoreEventRecord(id, simulationId, roundId, sourceEventId, agentId, scoreType, team, points,
                reason, agentBlocked, createdAt);
    }
}
