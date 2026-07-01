package com.cybersim.remediationservice.persistence;

import com.cybersim.remediationservice.model.RemediationRecord;
import com.cybersim.remediationservice.model.RemediationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "remediation_actions")
class RemediationEntity {
    @Id
    private UUID id;
    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;
    @Column(name = "round_id")
    private UUID roundId;
    @Column(name = "vulnerability_id", nullable = false)
    private UUID vulnerabilityId;
    @Column(name = "detection_id")
    private UUID detectionId;
    @Column(name = "agent_id", nullable = false)
    private UUID agentId;
    @Column(name = "target_id", nullable = false)
    private UUID targetId;
    @Enumerated(EnumType.STRING)
    @Column(name = "remediation_type", nullable = false, length = 60)
    private RemediationType remediationType;
    @Column(name = "patch_summary", nullable = false, length = 4000)
    private String patchSummary;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(name = "outcome_summary", length = 2000)
    private String outcomeSummary;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "applied_at")
    private Instant appliedAt;
    @Column(name = "verified_at")
    private Instant verifiedAt;
    @Column(name = "rolled_back_at")
    private Instant rolledBackAt;

    protected RemediationEntity() {
    }

    private RemediationEntity(RemediationRecord remediation) {
        id = remediation.id();
        simulationId = remediation.simulationId();
        roundId = remediation.roundId();
        vulnerabilityId = remediation.vulnerabilityId();
        detectionId = remediation.detectionId();
        agentId = remediation.agentId();
        targetId = remediation.targetId();
        remediationType = remediation.remediationType();
        patchSummary = remediation.patchSummary();
        status = remediation.status();
        outcomeSummary = remediation.outcomeSummary();
        createdAt = remediation.createdAt();
        updatedAt = remediation.updatedAt();
        approvedAt = remediation.approvedAt();
        appliedAt = remediation.appliedAt();
        verifiedAt = remediation.verifiedAt();
        rolledBackAt = remediation.rolledBackAt();
    }

    static RemediationEntity from(RemediationRecord remediation) {
        return new RemediationEntity(remediation);
    }

    RemediationRecord toRecord() {
        return new RemediationRecord(id, simulationId, roundId, vulnerabilityId, detectionId, agentId, targetId,
                remediationType, patchSummary, status, outcomeSummary, createdAt, updatedAt, approvedAt, appliedAt,
                verifiedAt, rolledBackAt);
    }
}
