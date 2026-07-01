package com.cybersim.verificationservice.workflow;

import com.cybersim.shared.dto.RemediationResponse;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface VerificationWorkflowClient {
    Optional<RemediationResponse> findRemediation(UUID remediationId);

    List<RemediationResponse> findRemediations(UUID simulationId);

    VerificationCheckResult verifyPatch(RemediationResponse remediation);

    boolean synchronizeOutcome(RemediationResponse remediation, VerificationCheckResult result);

    void completeVerificationStage(UUID simulationId, UUID roundId);
}
