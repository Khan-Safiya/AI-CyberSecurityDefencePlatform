package com.cybersim.shared.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RoundCompletionRequest(
        @Min(0) int newFindingsCount,
        @Min(0) int remediatedFindingsCount,
        @Min(0) int verifiedFixesCount,
        @Min(0) @Max(100) int riskScoreBefore,
        @Min(0) @Max(100) int riskScoreAfter,
        int redTeamScore,
        int blueTeamScore,
        boolean allCriticalAndHighFixed,
        boolean unsafeActionDetected,
        boolean targetAvailable,
        boolean policyViolationDetected
) {
}
