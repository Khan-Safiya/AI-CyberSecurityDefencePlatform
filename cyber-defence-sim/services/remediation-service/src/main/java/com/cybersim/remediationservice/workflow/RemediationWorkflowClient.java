package com.cybersim.remediationservice.workflow;

import com.cybersim.shared.dto.DetectionEventResponse;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.VulnerabilityResponse;

import java.util.List;
import java.util.UUID;

public interface RemediationWorkflowClient {
    List<VulnerabilityResponse> findings(UUID simulationId);
    List<DetectionEventResponse> detections(UUID simulationId);
    boolean policyAllows(PolicyEvaluationRequest request);
    void completeBlueTeamStage(UUID simulationId, UUID roundId);
}
