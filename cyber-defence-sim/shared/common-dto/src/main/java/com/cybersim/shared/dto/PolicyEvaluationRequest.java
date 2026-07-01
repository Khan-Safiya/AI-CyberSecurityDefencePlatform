package com.cybersim.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PolicyEvaluationRequest(
        @NotNull UUID simulationId,
        @NotNull UUID targetId,
        @NotNull UUID agentId,
        @NotBlank @Size(max = 100) String actionType,
        @NotBlank @Size(max = 253)
        @Pattern(regexp = "^[^\\s/:]+(?::\\d{1,5})?$") String host,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "^/.*$") String path,
        @NotBlank @Size(max = 16) String httpMethod
) {
}
