package com.cybersim.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerificationOutcomeRequest(
        @NotBlank @Pattern(regexp = "^(PASSED|FAILED|INCONCLUSIVE)$") String status,
        @NotBlank @Size(max = 4000) String evidenceSummary
) {
}
