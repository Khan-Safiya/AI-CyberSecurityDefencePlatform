package com.cybersim.verificationservice.model;

import com.cybersim.shared.dto.RemediationResponse;
import com.cybersim.shared.dto.VerificationResponse;
import com.cybersim.verificationservice.workflow.VerificationCheckResult;

import java.time.Instant;
import java.util.UUID;

public record VerificationRecord(
        UUID id,
        UUID simulationId,
        UUID roundId,
        UUID vulnerabilityId,
        UUID remediationId,
        UUID targetId,
        String status,
        String evidenceSummary,
        Instant verifiedAt
) {
    public static VerificationRecord from(RemediationResponse remediation, VerificationCheckResult result) {
        return from(remediation, result, UUID.randomUUID());
    }

    public static VerificationRecord from(RemediationResponse remediation, VerificationCheckResult result, UUID id) {
        return new VerificationRecord(id, remediation.simulationId(), remediation.roundId(),
                remediation.vulnerabilityId(), remediation.id(), remediation.targetId(), result.status(),
                result.evidenceSummary(), Instant.now());
    }

    public VerificationResponse toResponse() {
        return new VerificationResponse(id, simulationId, roundId, vulnerabilityId, remediationId, targetId,
                status, evidenceSummary, verifiedAt);
    }
}
