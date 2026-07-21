package com.cybersim.blueteamagentservice.client;

import com.cybersim.shared.dto.RemediationResponse;

import java.util.List;
import java.util.UUID;

public interface RemediationClient {
    List<RemediationResponse> forSimulation(UUID simulationId);
}
