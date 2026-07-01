package com.cybersim.remediationservice.model;

import com.cybersim.shared.dto.RemediationCreateRequest;
import com.cybersim.shared.dto.RemediationResponse;

import java.time.Instant;
import java.util.UUID;

public record RemediationRecord(
        UUID id,
        UUID simulationId,
        UUID roundId,
        UUID vulnerabilityId,
        UUID detectionId,
        UUID agentId,
        UUID targetId,
        RemediationType remediationType,
        String patchSummary,
        String status,
        String outcomeSummary,
        Instant createdAt,
        Instant updatedAt,
        Instant approvedAt,
        Instant appliedAt,
        Instant verifiedAt,
        Instant rolledBackAt
) {
    public static RemediationRecord from(RemediationCreateRequest request) {
        return from(request, UUID.randomUUID());
    }

    public static RemediationRecord from(RemediationCreateRequest request, UUID id) {
        Instant now = Instant.now();
        return new RemediationRecord(id, request.simulationId(), request.roundId(),
                request.vulnerabilityId(), request.detectionId(), request.agentId(), request.targetId(),
                RemediationType.valueOf(request.remediationType()), request.patchSummary(), "PROPOSED", null,
                now, now, null, null, null, null);
    }

    public boolean sameProposal(RemediationCreateRequest request) {
        return simulationId.equals(request.simulationId())
                && java.util.Objects.equals(roundId, request.roundId())
                && vulnerabilityId.equals(request.vulnerabilityId())
                && java.util.Objects.equals(detectionId, request.detectionId())
                && agentId.equals(request.agentId())
                && targetId.equals(request.targetId())
                && remediationType.name().equals(request.remediationType())
                && patchSummary.equals(request.patchSummary());
    }

    public RemediationRecord approve() {
        Instant now = Instant.now();
        return copy("APPROVED", "Remediation approved for safe application", now, now, appliedAt, rolledBackAt);
    }

    public RemediationRecord applied(boolean successful, String outcome) {
        Instant now = Instant.now();
        return copy(successful ? "APPLIED" : "FAILED", outcome, now, approvedAt,
                successful ? now : appliedAt, rolledBackAt);
    }

    public RemediationRecord rolledBack(boolean successful, String outcome) {
        Instant now = Instant.now();
        return copy(successful ? "ROLLED_BACK" : "FAILED", outcome, now, approvedAt, appliedAt,
                successful ? now : rolledBackAt);
    }

    public RemediationRecord withVerificationOutcome(String verificationStatus, String evidenceSummary) {
        Instant now = Instant.now();
        String nextStatus = switch (verificationStatus) {
            case "PASSED" -> "VERIFIED";
            case "FAILED" -> "FAILED";
            default -> status;
        };
        return new RemediationRecord(id, simulationId, roundId, vulnerabilityId, detectionId, agentId, targetId,
                remediationType, patchSummary, nextStatus, evidenceSummary, createdAt, now, approvedAt, appliedAt,
                "PASSED".equals(verificationStatus) ? now : verifiedAt, rolledBackAt);
    }

    public RemediationResponse toResponse() {
        return new RemediationResponse(id, simulationId, roundId, vulnerabilityId, detectionId, agentId, targetId,
                remediationType.name(), patchSummary, status, outcomeSummary, createdAt, updatedAt, approvedAt,
                appliedAt, verifiedAt, rolledBackAt);
    }

    private RemediationRecord copy(
            String nextStatus,
            String nextOutcome,
            Instant nextUpdatedAt,
            Instant nextApprovedAt,
            Instant nextAppliedAt,
            Instant nextRolledBackAt
    ) {
        return new RemediationRecord(id, simulationId, roundId, vulnerabilityId, detectionId, agentId, targetId,
                remediationType, patchSummary, nextStatus, nextOutcome, createdAt, nextUpdatedAt, nextApprovedAt,
                nextAppliedAt, verifiedAt, nextRolledBackAt);
    }
}
