package com.cybersim.simulationorchestratorservice.persistence;

import com.cybersim.shared.dto.TargetMode;
import com.cybersim.simulationorchestratorservice.model.SimulationRecord;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "simulations")
class SimulationEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TargetMode mode;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "current_round", nullable = false)
    private int currentRound;

    @Column(name = "max_rounds", nullable = false)
    private int maxRounds;

    @Column(name = "max_duration_minutes", nullable = false)
    private int maxDurationMinutes;

    @Column(name = "stop_when_no_new_findings_for_rounds", nullable = false)
    private int stopWhenNoNewFindingsForRounds;

    @Column(name = "minimum_accepted_risk_level", nullable = false, length = 20)
    private String minimumAcceptedRiskLevel;

    @Column(name = "retest_enabled", nullable = false)
    private boolean retestEnabled;

    @Column(name = "retest_delay_seconds", nullable = false)
    private int retestDelaySeconds;

    @Column(name = "red_team_score", nullable = false)
    private int redTeamScore;

    @Column(name = "blue_team_score", nullable = false)
    private int blueTeamScore;

    @Column(name = "final_risk_score", nullable = false)
    private int finalRiskScore;

    @Column(name = "stop_reason", length = 100)
    private String stopReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "simulation_timeline", joinColumns = @JoinColumn(name = "simulation_id"))
    @OrderColumn(name = "position")
    @Column(name = "timeline_entry", nullable = false, length = 2000)
    private List<String> timeline = new ArrayList<>();

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected SimulationEntity() {
    }

    private SimulationEntity(SimulationRecord record) {
        id = record.id();
        name = record.name();
        mode = record.mode();
        targetId = record.targetId();
        status = record.status();
        currentRound = record.currentRound();
        maxRounds = record.maxRounds();
        maxDurationMinutes = record.maxDurationMinutes();
        stopWhenNoNewFindingsForRounds = record.stopWhenNoNewFindingsForRounds();
        minimumAcceptedRiskLevel = record.minimumAcceptedRiskLevel();
        retestEnabled = record.retestEnabled();
        retestDelaySeconds = record.retestDelaySeconds();
        redTeamScore = record.redTeamScore();
        blueTeamScore = record.blueTeamScore();
        finalRiskScore = record.finalRiskScore();
        stopReason = record.stopReason();
        timeline = new ArrayList<>(record.timeline());
        startedAt = record.startedAt();
        endedAt = record.endedAt();
    }

    static SimulationEntity from(SimulationRecord record) {
        return new SimulationEntity(record);
    }

    SimulationRecord toRecord() {
        return new SimulationRecord(id, name, mode, targetId, status, currentRound, maxRounds, maxDurationMinutes,
                stopWhenNoNewFindingsForRounds, minimumAcceptedRiskLevel, retestEnabled, retestDelaySeconds,
                redTeamScore, blueTeamScore, finalRiskScore, stopReason, List.copyOf(timeline), startedAt, endedAt);
    }
}
