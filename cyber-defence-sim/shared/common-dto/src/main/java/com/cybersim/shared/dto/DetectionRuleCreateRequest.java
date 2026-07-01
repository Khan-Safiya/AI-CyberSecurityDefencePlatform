package com.cybersim.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DetectionRuleCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 2000) String eventPattern,
        @NotBlank @Pattern(regexp = "^(INFO|LOW|MEDIUM|HIGH|CRITICAL)$") String severity,
        @NotNull Boolean enabled
) {
}
