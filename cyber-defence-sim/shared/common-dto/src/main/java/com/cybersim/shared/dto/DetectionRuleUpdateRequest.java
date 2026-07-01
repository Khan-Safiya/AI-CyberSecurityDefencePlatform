package com.cybersim.shared.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DetectionRuleUpdateRequest(
        @Size(max = 200) @Pattern(regexp = "(?s).*\\S.*") String name,
        @Size(max = 2000) @Pattern(regexp = "(?s).*\\S.*") String description,
        @Size(max = 2000) @Pattern(regexp = "(?s).*\\S.*") String eventPattern,
        @Pattern(regexp = "^(INFO|LOW|MEDIUM|HIGH|CRITICAL)$") String severity,
        Boolean enabled
) {
    @AssertTrue(message = "at least one detection rule field must be provided")
    public boolean hasUpdate() {
        return name != null || description != null || eventPattern != null || severity != null || enabled != null;
    }
}
