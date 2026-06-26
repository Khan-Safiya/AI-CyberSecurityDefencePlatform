package com.cybersim.shared.dto;

import java.util.List;

public record TargetRegistrationRequest(
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
        boolean writtenAuthorizationConfirmed
) {
}
