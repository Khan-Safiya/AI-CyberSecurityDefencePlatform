package com.cybersim.detectionengineservice.workflow;

import com.cybersim.shared.dto.VulnerabilityResponse;

import java.util.List;
import java.util.UUID;

public interface DetectionWorkflowClient {
    List<VulnerabilityResponse> findings(UUID simulationId);
    void completeDetectionStage(UUID simulationId, UUID roundId);
}
