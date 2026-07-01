package com.cybersim.scoringservice.workflow;
import com.cybersim.shared.dto.*;
import java.util.*;
public interface ScoringWorkflowClient {
    SimulationResponse simulation(UUID id);
    List<VulnerabilityResponse> findings(UUID id);
    List<DetectionEventResponse> detections(UUID id);
    List<RemediationResponse> remediations(UUID id);
    List<VerificationResponse> verifications(UUID id);
    void completeRound(UUID simulationId, UUID roundId, RoundCompletionRequest request);
}
