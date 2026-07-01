package com.cybersim.simulationorchestratorservice.persistence;

import com.cybersim.simulationorchestratorservice.model.SimulationRoundRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulation_rounds")
class SimulationRoundEntity {
    @Id
    private UUID id;
    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;
    @Column(name = "round_number", nullable = false)
    private int roundNumber;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "new_findings_count", nullable = false)
    private int newFindingsCount;
    @Column(name = "remediated_findings_count", nullable = false)
    private int remediatedFindingsCount;
    @Column(name = "verified_fixes_count", nullable = false)
    private int verifiedFixesCount;
    @Column(name = "risk_score_before", nullable = false)
    private int riskScoreBefore;
    @Column(name = "risk_score_after", nullable = false)
    private int riskScoreAfter;

    protected SimulationRoundEntity() {
    }

    private SimulationRoundEntity(SimulationRoundRecord round) {
        id = round.id();
        simulationId = round.simulationId();
        roundNumber = round.roundNumber();
        status = round.status();
        startedAt = round.startedAt();
        endedAt = round.endedAt();
        newFindingsCount = round.newFindingsCount();
        remediatedFindingsCount = round.remediatedFindingsCount();
        verifiedFixesCount = round.verifiedFixesCount();
        riskScoreBefore = round.riskScoreBefore();
        riskScoreAfter = round.riskScoreAfter();
    }

    static SimulationRoundEntity from(SimulationRoundRecord round) {
        return new SimulationRoundEntity(round);
    }

    SimulationRoundRecord toRecord() {
        return new SimulationRoundRecord(id, simulationId, roundNumber, status, startedAt, endedAt,
                newFindingsCount, remediatedFindingsCount, verifiedFixesCount, riskScoreBefore, riskScoreAfter);
    }
}
