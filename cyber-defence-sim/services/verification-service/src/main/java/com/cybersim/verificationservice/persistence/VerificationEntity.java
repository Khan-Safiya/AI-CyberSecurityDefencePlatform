package com.cybersim.verificationservice.persistence;

import com.cybersim.verificationservice.model.VerificationRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_results")
class VerificationEntity {
    @Id
    private UUID id;
    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;
    @Column(name = "round_id")
    private UUID roundId;
    @Column(name = "vulnerability_id", nullable = false)
    private UUID vulnerabilityId;
    @Column(name = "remediation_id", nullable = false)
    private UUID remediationId;
    @Column(name = "target_id", nullable = false)
    private UUID targetId;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "evidence_summary", nullable = false, length = 4000)
    private String evidenceSummary;
    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    protected VerificationEntity() {
    }

    private VerificationEntity(VerificationRecord verification) {
        id = verification.id();
        simulationId = verification.simulationId();
        roundId = verification.roundId();
        vulnerabilityId = verification.vulnerabilityId();
        remediationId = verification.remediationId();
        targetId = verification.targetId();
        status = verification.status();
        evidenceSummary = verification.evidenceSummary();
        verifiedAt = verification.verifiedAt();
    }

    static VerificationEntity from(VerificationRecord verification) {
        return new VerificationEntity(verification);
    }

    VerificationRecord toRecord() {
        return new VerificationRecord(id, simulationId, roundId, vulnerabilityId, remediationId, targetId, status,
                evidenceSummary, verifiedAt);
    }
}
