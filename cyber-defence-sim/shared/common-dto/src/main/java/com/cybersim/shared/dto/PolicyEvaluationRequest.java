package com.cybersim.shared.dto;

import java.util.UUID;

public record PolicyEvaluationRequest(
        UUID simulationId,
        UUID targetId,
        UUID agentId,
        String actionType,
        String host,
        String path,
        String httpMethod
) {
}
