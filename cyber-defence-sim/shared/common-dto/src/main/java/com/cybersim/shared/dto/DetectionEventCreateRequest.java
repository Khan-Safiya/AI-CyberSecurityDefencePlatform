package com.cybersim.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record DetectionEventCreateRequest(
        @NotNull UUID simulationId,
        UUID roundId,
        @NotNull UUID targetId,
        @NotBlank @Size(max = 100) String source,
        @NotBlank @Size(max = 100) String eventType,
        @NotBlank @Pattern(regexp = "^(INFO|LOW|MEDIUM|HIGH|CRITICAL)$") String severity,
        @NotBlank @Size(max = 2000) String message,
        UUID relatedActionId,
        UUID relatedVulnerabilityId,
        @NotNull @Size(max = 50) Map<@Size(min = 1, max = 100) String, @Size(max = 1000) String> metadata
) {
}
