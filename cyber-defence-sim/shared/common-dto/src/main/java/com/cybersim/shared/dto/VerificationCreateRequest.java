package com.cybersim.shared.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VerificationCreateRequest(@NotNull UUID remediationId) {
}
