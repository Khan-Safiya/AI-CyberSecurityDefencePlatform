package com.cybersim.targetregistryservice.model;

import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.dto.TargetRegistrationRequest;
import com.cybersim.shared.dto.TargetResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TargetRecord(
        UUID id,
        String name,
        String description,
        TargetMode mode,
        String baseUrl,
        String environmentType,
        List<String> allowedHosts,
        List<String> allowedPaths,
        List<String> excludedPaths,
        List<String> allowedHttpMethods,
        int maxRequestsPerMinute,
        boolean writtenAuthorizationConfirmed,
        String ownershipVerificationStatus,
        String status,
        String verificationToken,
        Instant createdAt,
        Instant updatedAt
) {
    public static TargetRecord from(TargetRegistrationRequest request, String verification, String status) {
        Instant now = Instant.now();
        return new TargetRecord(
                UUID.randomUUID(), request.name(), request.description(), request.mode(), request.baseUrl(),
                request.environmentType(), List.copyOf(request.allowedHosts()), List.copyOf(request.allowedPaths()),
                request.excludedPaths() == null ? List.of() : List.copyOf(request.excludedPaths()),
                List.copyOf(request.allowedHttpMethods()), request.maxRequestsPerMinute(),
                request.writtenAuthorizationConfirmed(), verification, status, UUID.randomUUID().toString(), now, now
        );
    }

    public TargetRecord withState(String verification, String status) {
        return new TargetRecord(id, name, description, mode, baseUrl, environmentType, allowedHosts, allowedPaths,
                excludedPaths, allowedHttpMethods, maxRequestsPerMinute, writtenAuthorizationConfirmed,
                verification, status, verificationToken, createdAt, Instant.now());
    }

    public TargetResponse toResponse() {
        return new TargetResponse(id, name, mode, baseUrl, environmentType, allowedHosts, allowedPaths,
                allowedHttpMethods, ownershipVerificationStatus, status, verificationToken, createdAt);
    }
}
