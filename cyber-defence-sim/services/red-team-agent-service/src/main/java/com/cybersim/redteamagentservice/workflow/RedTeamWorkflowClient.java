package com.cybersim.redteamagentservice.workflow;

import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.SimulationResponse;
import com.cybersim.shared.dto.TargetResponse;
import com.cybersim.shared.dto.VulnerabilityCreateRequest;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface RedTeamWorkflowClient {
    SimulationResponse simulation(UUID simulationId);
    Map<String, Boolean> sandboxPatchStates();
    Optional<TargetResponse> target(UUID targetId);
    boolean policyAllows(PolicyEvaluationRequest request);
    void createFinding(UUID idempotencyKey, VulnerabilityCreateRequest request);
    void completeRedTeamStage(UUID simulationId, UUID roundId);
}
