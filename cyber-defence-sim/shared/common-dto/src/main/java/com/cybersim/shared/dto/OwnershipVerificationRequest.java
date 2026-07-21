package com.cybersim.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnershipVerificationRequest(
        @NotBlank
        @Size(max = 128)
        String verificationToken
) {
}
