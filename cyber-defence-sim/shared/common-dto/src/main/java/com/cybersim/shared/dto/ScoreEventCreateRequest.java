package com.cybersim.shared.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record ScoreEventCreateRequest(
        @NotNull UUID simulationId,
        UUID roundId,
        @NotNull UUID sourceEventId,
        @NotBlank @Pattern(regexp = "^(RED_LOW_FINDING|RED_MEDIUM_FINDING|RED_HIGH_FINDING|RED_CRITICAL_FINDING|RED_DUPLICATE_FINDING|RED_UNSAFE_ACTION|BLUE_VALID_DETECTION|BLUE_CORRECT_TRIAGE|BLUE_VALID_REMEDIATION_PROPOSAL|BLUE_PATCH_APPLIED|BLUE_FIX_VERIFIED|BLUE_FALSE_POSITIVE|BLUE_FAILED_PATCH)$") String scoreType,
        UUID agentId
) {
    @AssertTrue(message = "agentId is required for an unsafe action penalty")
    public boolean hasAgentForUnsafeAction() {
        return !"RED_UNSAFE_ACTION".equals(scoreType) || agentId != null;
    }
}
