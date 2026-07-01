package com.cybersim.verificationservice.workflow;

import com.cybersim.shared.dto.RemediationResponse;
import com.cybersim.verificationservice.model.VerificationRecord;
import com.cybersim.verificationservice.store.VerificationStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class VerificationStageProcessor {
    private final VerificationStore store;
    private final VerificationWorkflowClient workflowClient;

    public VerificationStageProcessor(VerificationStore store, VerificationWorkflowClient workflowClient) {
        this.store = store;
        this.workflowClient = workflowClient;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int verify(UUID messageId, UUID simulationId, UUID roundId, List<RemediationResponse> remediations) {
        int created = 0;
        for (RemediationResponse remediation : remediations) {
            if (!simulationId.equals(remediation.simulationId()) || !roundId.equals(remediation.roundId())) continue;
            boolean verifiable = "APPLIED".equals(remediation.status()) || "VERIFIED".equals(remediation.status())
                    || ("FAILED".equals(remediation.status()) && remediation.appliedAt() != null);
            if (!verifiable) {
                throw new IllegalStateException("Current-round remediation is not applied: " + remediation.id());
            }
            UUID verificationId = UUID.nameUUIDFromBytes((messageId + ":" + remediation.id())
                    .getBytes(StandardCharsets.UTF_8));
            VerificationRecord existing = store.findById(verificationId).orElse(null);
            if (existing != null) {
                if (!existing.remediationId().equals(remediation.id())) {
                    throw new IllegalStateException("Deterministic verification ID belongs to another remediation");
                }
                continue;
            }
            VerificationCheckResult result = workflowClient.verifyPatch(remediation);
            if (!workflowClient.synchronizeOutcome(remediation, result)) {
                throw new IllegalStateException("Verification outcome synchronization failed: " + remediation.id());
            }
            store.save(VerificationRecord.from(remediation, result, verificationId));
            created++;
        }
        return created;
    }
}
