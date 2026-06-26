package com.cybersim.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TargetResponse(
        UUID id,
        String name,
        TargetMode mode,
        String baseUrl,
        String environmentType,
        List<String> allowedHosts,
        List<String> allowedPaths,
        List<String> allowedHttpMethods,
        String ownershipVerificationStatus,
        String status,
        String verificationToken,
        Instant createdAt
) {
}
