package com.cybersim.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RemediationCreateRequest(
        @NotNull UUID simulationId,
        UUID roundId,
        @NotNull UUID vulnerabilityId,
        UUID detectionId,
        @NotNull UUID agentId,
        @NotNull UUID targetId,
        @NotBlank @Pattern(regexp = "^(AUTH_REQUIRED|OBJECT_AUTHORIZATION|RATE_LIMIT|DISABLE_DEBUG_ENDPOINT|INPUT_VALIDATION|UPDATE_DEPENDENCY_METADATA)$") String remediationType,
        @NotBlank @Size(max = 4000) String patchSummary
) {
}
