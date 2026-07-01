package com.cybersim.shared.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TargetRegistrationRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull TargetMode mode,
        @NotBlank @Size(max = 2048)
        @Pattern(regexp = "(?i)^https?://[^\\s/]+(?:/.*)?$") String baseUrl,
        @NotBlank
        @Pattern(regexp = "(?i)^(SANDBOX|STAGING|PRODUCTION|PRODUCTION_BLOCKED)$") String environmentType,
        @NotEmpty @Size(max = 100)
        List<@NotBlank @Size(max = 253) @Pattern(regexp = "^[^\\s/:]+(?::\\d{1,5})?$") String> allowedHosts,
        @NotEmpty @Size(max = 100)
        List<@NotBlank @Size(max = 2048) @Pattern(regexp = "^/.*$") String> allowedPaths,
        @Size(max = 100)
        List<@NotBlank @Size(max = 2048) @Pattern(regexp = "^/.*$") String> excludedPaths,
        @NotEmpty @Size(max = 10)
        List<@NotBlank @Pattern(regexp = "(?i)^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$") String> allowedHttpMethods,
        @Min(1) @Max(10_000) int maxRequestsPerMinute,
        boolean writtenAuthorizationConfirmed
) {
}
