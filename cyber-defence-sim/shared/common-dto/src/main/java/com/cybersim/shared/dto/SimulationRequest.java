package com.cybersim.shared.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SimulationRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull TargetMode mode,
        @NotNull UUID targetId,
        @Min(1) @Max(100) int maxRounds,
        @Min(1) @Max(1440) int maxDurationMinutes,
        @Min(1) @Max(100) int stopWhenNoNewFindingsForRounds,
        @Pattern(regexp = "^(LOW|MEDIUM|HIGH|CRITICAL)$") String minimumAcceptedRiskLevel,
        Boolean retestEnabled,
        @Min(0) @Max(3600) Integer retestDelaySeconds
) {
    public SimulationRequest {
        minimumAcceptedRiskLevel = minimumAcceptedRiskLevel == null ? "LOW" : minimumAcceptedRiskLevel;
        retestEnabled = retestEnabled == null ? Boolean.TRUE : retestEnabled;
        retestDelaySeconds = retestDelaySeconds == null ? 10 : retestDelaySeconds;
    }

    public SimulationRequest(
            String name,
            TargetMode mode,
            UUID targetId,
            int maxRounds,
            int maxDurationMinutes,
            int stopWhenNoNewFindingsForRounds
    ) {
        this(name, mode, targetId, maxRounds, maxDurationMinutes, stopWhenNoNewFindingsForRounds,
                "LOW", true, 10);
    }

    @AssertTrue(message = "stop threshold must not exceed maximum rounds")
    public boolean isStoppingThresholdValid() {
        return stopWhenNoNewFindingsForRounds <= maxRounds;
    }
}
