package com.cybersim.remediationservice.workflow;

import com.cybersim.remediationservice.model.RemediationRecord;
import com.cybersim.remediationservice.model.RemediationType;
import com.cybersim.remediationservice.patch.PatchExecutionResult;
import com.cybersim.remediationservice.patch.SandboxPatchClient;
import com.cybersim.remediationservice.store.RemediationStore;
import com.cybersim.shared.dto.DetectionEventResponse;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.RemediationCreateRequest;
import com.cybersim.shared.dto.VulnerabilityResponse;
import com.cybersim.shared.exceptions.ConflictException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RemediationStageProcessor {
    static final UUID BLUE_TEAM_AGENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    static final UUID SANDBOX_TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final Map<String, Plan> PLANS = Map.of(
            "AUTHENTICATION", new Plan("AUTH_REQUIRED", "auth-required", "Require authentication for the administrative report."),
            "ACCESS_CONTROL", new Plan("OBJECT_AUTHORIZATION", "object-authorization", "Enforce ownership checks for user documents."),
            "RATE_LIMIT", new Plan("RATE_LIMIT", "rate-limit", "Limit repeated login requests."),
            "CONFIG_EXPOSURE", new Plan("DISABLE_DEBUG_ENDPOINT", "disable-debug-endpoint", "Disable the debug configuration endpoint."),
            "INPUT_VALIDATION", new Plan("INPUT_VALIDATION", "input-validation", "Validate billing search input."),
            "DEPENDENCY_RISK", new Plan("UPDATE_DEPENDENCY_METADATA", "update-dependency-metadata", "Update simulated dependency metadata."));

    private final RemediationStore store;
    private final SandboxPatchClient patchClient;
    private final RemediationWorkflowClient workflowClient;

    public RemediationStageProcessor(RemediationStore store, SandboxPatchClient patchClient,
                                     RemediationWorkflowClient workflowClient) {
        this.store = store;
        this.patchClient = patchClient;
        this.workflowClient = workflowClient;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult persistAndApply(UUID messageId, UUID simulationId, UUID roundId,
                                       List<VulnerabilityResponse> findings,
                                       List<DetectionEventResponse> detections) {
        Map<UUID, UUID> detectionByFinding = new HashMap<>();
        detections.stream()
                .filter(detection -> simulationId.equals(detection.simulationId()))
                .filter(detection -> roundId.equals(detection.roundId()))
                .filter(detection -> detection.relatedVulnerabilityId() != null)
                .forEach(detection -> detectionByFinding.putIfAbsent(detection.relatedVulnerabilityId(), detection.id()));

        int appliedCount = 0;
        boolean allSuccessful = true;
        for (VulnerabilityResponse finding : findings) {
            if (!simulationId.equals(finding.simulationId()) || !roundId.equals(finding.roundId())) {
                continue;
            }
            if (!SANDBOX_TARGET_ID.equals(finding.targetId())) {
                throw new IllegalStateException("Automated remediation is restricted to the built-in sandbox target");
            }
            Plan plan = PLANS.get(finding.type());
            if (plan == null) {
                throw new IllegalStateException("No allowlisted remediation maps vulnerability type: " + finding.type());
            }
            UUID remediationId = UUID.nameUUIDFromBytes((messageId + ":" + finding.id())
                    .getBytes(StandardCharsets.UTF_8));
            RemediationCreateRequest request = new RemediationCreateRequest(simulationId, roundId, finding.id(),
                    detectionByFinding.get(finding.id()), BLUE_TEAM_AGENT_ID, finding.targetId(), plan.type(), plan.summary());
            RemediationRecord remediation = store.findById(remediationId).orElse(null);
            if (remediation == null) {
                remediation = store.save(RemediationRecord.from(request, remediationId));
            } else if (!remediation.sameProposal(request)) {
                throw new ConflictException("Deterministic remediation ID belongs to different proposal data");
            }
            if ("APPLIED".equals(remediation.status()) || "VERIFIED".equals(remediation.status())) {
                continue;
            }
            if ("PROPOSED".equals(remediation.status())) {
                remediation = store.save(remediation.approve());
            }
            if (!"APPROVED".equals(remediation.status()) && !"FAILED".equals(remediation.status())) {
                throw new IllegalStateException("Remediation cannot be applied from status: " + remediation.status());
            }
            PolicyEvaluationRequest policy = new PolicyEvaluationRequest(simulationId, finding.targetId(),
                    BLUE_TEAM_AGENT_ID, "APPLY_REMEDIATION", "target-system-service",
                    "/internal/patches/" + plan.patchName(), "POST");
            if (!workflowClient.policyAllows(policy)) {
                throw new IllegalStateException("Policy denied remediation: " + plan.type());
            }
            PatchExecutionResult result = patchClient.apply(RemediationType.valueOf(plan.type()));
            store.save(remediation.applied(result.successful(), result.summary()));
            if (result.successful()) {
                appliedCount++;
            } else {
                allSuccessful = false;
            }
        }
        return new BatchResult(appliedCount, allSuccessful);
    }

    public record BatchResult(int appliedCount, boolean successful) { }
    private record Plan(String type, String patchName, String summary) { }
}
