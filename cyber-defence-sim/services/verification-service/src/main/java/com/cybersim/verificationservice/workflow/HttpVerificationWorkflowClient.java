package com.cybersim.verificationservice.workflow;

import com.cybersim.shared.dto.RemediationResponse;
import com.cybersim.shared.dto.VerificationOutcomeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class HttpVerificationWorkflowClient implements VerificationWorkflowClient {
    private static final UUID SANDBOX_TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final Map<String, String> PATCH_NAMES = Map.of(
            "AUTH_REQUIRED", "auth-required",
            "OBJECT_AUTHORIZATION", "object-authorization",
            "RATE_LIMIT", "rate-limit",
            "DISABLE_DEBUG_ENDPOINT", "disable-debug-endpoint",
            "INPUT_VALIDATION", "input-validation",
            "UPDATE_DEPENDENCY_METADATA", "update-dependency-metadata"
    );

    private final RestClient remediationClient;
    private final RestClient vulnerabilityClient;
    private final RestClient targetClient;
    private final RestClient simulationClient;

    @Autowired
    HttpVerificationWorkflowClient(
            @Value("${verification.remediation-base-url}") String remediationBaseUrl,
            @Value("${verification.vulnerability-base-url}") String vulnerabilityBaseUrl,
            @Value("${verification.target-system-base-url}") String targetBaseUrl,
            @Value("${verification.simulation-base-url}") String simulationBaseUrl,
            @Value("${verification.service-auth-token}") String serviceAuthToken
    ) {
        this(client(remediationBaseUrl, serviceAuthToken), client(vulnerabilityBaseUrl, serviceAuthToken),
                client(targetBaseUrl, serviceAuthToken), client(simulationBaseUrl, serviceAuthToken));
    }

    HttpVerificationWorkflowClient(RestClient remediationClient, RestClient vulnerabilityClient,
                                   RestClient targetClient, RestClient simulationClient) {
        this.remediationClient = remediationClient;
        this.vulnerabilityClient = vulnerabilityClient;
        this.targetClient = targetClient;
        this.simulationClient = simulationClient;
    }

    @Override
    public Optional<RemediationResponse> findRemediation(UUID remediationId) {
        try {
            return Optional.ofNullable(remediationClient.get().uri("/remediations/{id}", remediationId)
                    .retrieve().body(RemediationResponse.class));
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<RemediationResponse> findRemediations(UUID simulationId) {
        List<RemediationResponse> values = remediationClient.get().uri("/simulations/{id}/remediations", simulationId)
                .retrieve().body(new ParameterizedTypeReference<>() { });
        return values == null ? List.of() : values;
    }

    @Override
    public VerificationCheckResult verifyPatch(RemediationResponse remediation) {
        if (!SANDBOX_TARGET_ID.equals(remediation.targetId())) {
            return new VerificationCheckResult("INCONCLUSIVE",
                    "Automated verification is limited to the built-in sandbox target.");
        }
        String patchName = PATCH_NAMES.get(remediation.remediationType());
        if (patchName == null) {
            return new VerificationCheckResult("INCONCLUSIVE", "The remediation type has no safe verification mapping.");
        }
        try {
            Map<String, Boolean> states = targetClient.get().uri("/internal/patches/status").retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            if (states == null || !states.containsKey(patchName)) {
                return new VerificationCheckResult("INCONCLUSIVE", "The sandbox did not report the expected patch state.");
            }
            if (Boolean.TRUE.equals(states.get(patchName))) {
                return new VerificationCheckResult("PASSED", "The sandbox reports the expected patch as applied.");
            }
            return new VerificationCheckResult("FAILED", "The sandbox reports the expected patch as not applied.");
        } catch (RestClientException exception) {
            return new VerificationCheckResult("INCONCLUSIVE", "The sandbox patch-status service is unavailable.");
        }
    }

    @Override
    public boolean synchronizeOutcome(RemediationResponse remediation, VerificationCheckResult result) {
        VerificationOutcomeRequest request = new VerificationOutcomeRequest(result.status(), result.evidenceSummary());
        try {
            remediationClient.post().uri("/internal/remediations/{id}/verification-result", remediation.id())
                    .body(request).retrieve().toBodilessEntity();
            vulnerabilityClient.post().uri("/internal/vulnerabilities/{id}/verification-result", remediation.vulnerabilityId())
                    .body(request).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            return false;
        }
    }

    @Override
    public void completeVerificationStage(UUID simulationId, UUID roundId) {
        simulationClient.post().uri("/simulations/{simulationId}/rounds/{roundId}/verification-complete",
                simulationId, roundId).retrieve().toBodilessEntity();
    }

    private static RestClient client(String baseUrl, String serviceAuthToken) {
        return RestClient.builder().baseUrl(baseUrl).defaultHeader("X-Service-Token", serviceAuthToken).build();
    }
}
