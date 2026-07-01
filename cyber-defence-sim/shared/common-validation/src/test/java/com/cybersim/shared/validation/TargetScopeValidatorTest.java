package com.cybersim.shared.validation;

import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.dto.TargetRegistrationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TargetScopeValidatorTest {
    @Test
    void acceptsConsistentPublicStagingScope() {
        assertThat(TargetScopeValidator.findViolation(request(
                TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com",
                List.of("staging.example.com"),
                List.of("/demo/**")
        ))).isEmpty();
    }

    @Test
    void rejectsBaseUrlHostOutsideAllowedHosts() {
        assertViolation(
                request(TargetMode.EXTERNAL_STAGING_TARGET, "https://other.example.com",
                        List.of("staging.example.com"), List.of("/demo/**")),
                "exactly match"
        );
    }

    @Test
    void rejectsEmbeddedCredentials() {
        assertViolation(
                request(TargetMode.EXTERNAL_STAGING_TARGET, "https://user:secret@staging.example.com",
                        List.of("staging.example.com"), List.of("/demo/**")),
                "credentials"
        );
    }

    @Test
    void rejectsPrivateAndMetadataNetworkTargets() {
        assertViolation(
                request(TargetMode.EXTERNAL_STAGING_TARGET, "http://169.254.169.254",
                        List.of("169.254.169.254"), List.of("/")),
                "private"
        );
        assertViolation(
                request(TargetMode.EXTERNAL_STAGING_TARGET, "http://service.internal",
                        List.of("service.internal"), List.of("/")),
                "private"
        );
    }

    @Test
    void requiresCustomPortInAllowedHost() {
        assertViolation(
                request(TargetMode.EXTERNAL_STAGING_TARGET, "https://staging.example.com:8443",
                        List.of("staging.example.com"), List.of("/")),
                "exactly match"
        );
        assertThat(TargetScopeValidator.findViolation(request(
                TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com:8443",
                List.of("staging.example.com:8443"),
                List.of("/")
        ))).isEmpty();
    }

    @Test
    void rejectsTraversalAndEncodedSeparatorsInScopePaths() {
        assertViolation(
                request(TargetMode.EXTERNAL_STAGING_TARGET, "https://staging.example.com",
                        List.of("staging.example.com"), List.of("/demo/../admin")),
                "traversal"
        );
        assertViolation(
                request(TargetMode.EXTERNAL_STAGING_TARGET, "https://staging.example.com",
                        List.of("staging.example.com"), List.of("/demo/%2fadmin")),
                "encoded"
        );
        assertViolation(
                request(TargetMode.EXTERNAL_STAGING_TARGET, "https://staging.example.com/demo/../admin",
                        List.of("staging.example.com"), List.of("/demo/**")),
                "base URL"
        );
    }

    @Test
    void rejectsAmbiguousNumericIpNotation() {
        assertViolation(
                request(TargetMode.EXTERNAL_STAGING_TARGET, "http://0177.0.0.1",
                        List.of("0177.0.0.1"), List.of("/")),
                "invalid"
        );
    }

    @Test
    void allowsLocalHostnameOnlyForInternalSandbox() {
        assertThat(TargetScopeValidator.findViolation(request(
                TargetMode.INTERNAL_SANDBOX,
                "http://target-system-service",
                List.of("target-system-service"),
                List.of("/demo/**")
        ))).isEmpty();
    }

    private TargetRegistrationRequest request(
            TargetMode mode,
            String baseUrl,
            List<String> allowedHosts,
            List<String> allowedPaths
    ) {
        return new TargetRegistrationRequest(
                "Test target",
                "Target scope validation test",
                mode,
                baseUrl,
                mode == TargetMode.INTERNAL_SANDBOX ? "SANDBOX" : "STAGING",
                allowedHosts,
                allowedPaths,
                List.of(),
                List.of("GET"),
                60,
                true
        );
    }

    private void assertViolation(TargetRegistrationRequest request, String expectedText) {
        assertThat(TargetScopeValidator.findViolation(request))
                .hasValueSatisfying(message -> assertThat(message).containsIgnoringCase(expectedText));
    }
}
