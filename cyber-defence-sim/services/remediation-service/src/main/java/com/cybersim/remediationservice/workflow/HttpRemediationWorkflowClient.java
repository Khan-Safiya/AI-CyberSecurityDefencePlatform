package com.cybersim.remediationservice.workflow;

import com.cybersim.shared.dto.DetectionEventResponse;
import com.cybersim.shared.dto.PolicyDecisionResponse;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.VulnerabilityResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
class HttpRemediationWorkflowClient implements RemediationWorkflowClient {
    private final RestClient vulnerabilityClient;
    private final RestClient detectionClient;
    private final RestClient policyClient;
    private final RestClient simulationClient;

    HttpRemediationWorkflowClient(
            @Value("${remediation.vulnerability-base-url}") String vulnerabilityUrl,
            @Value("${remediation.detection-base-url}") String detectionUrl,
            @Value("${remediation.policy-base-url}") String policyUrl,
            @Value("${remediation.simulation-base-url}") String simulationUrl,
            @Value("${remediation.service-auth-token}") String serviceToken
    ) {
        vulnerabilityClient = client(vulnerabilityUrl, serviceToken);
        detectionClient = client(detectionUrl, serviceToken);
        policyClient = client(policyUrl, serviceToken);
        simulationClient = client(simulationUrl, serviceToken);
    }

    @Override
    public List<VulnerabilityResponse> findings(UUID simulationId) {
        List<VulnerabilityResponse> values = vulnerabilityClient.get()
                .uri("/simulations/{id}/vulnerabilities", simulationId)
                .retrieve().body(new ParameterizedTypeReference<>() { });
        return values == null ? List.of() : values;
    }

    @Override
    public List<DetectionEventResponse> detections(UUID simulationId) {
        List<DetectionEventResponse> values = detectionClient.get()
                .uri("/simulations/{id}/detections", simulationId)
                .retrieve().body(new ParameterizedTypeReference<>() { });
        return values == null ? List.of() : values;
    }

    @Override
    public boolean policyAllows(PolicyEvaluationRequest request) {
        PolicyDecisionResponse response = policyClient.post().uri("/policies/evaluate-action")
                .body(request).retrieve().body(PolicyDecisionResponse.class);
        return response != null && response.allowed();
    }

    @Override
    public void completeBlueTeamStage(UUID simulationId, UUID roundId) {
        simulationClient.post().uri("/simulations/{simulationId}/rounds/{roundId}/blue-team-complete",
                simulationId, roundId).retrieve().toBodilessEntity();
    }

    private static RestClient client(String baseUrl, String serviceToken) {
        return RestClient.builder().baseUrl(baseUrl).defaultHeader("X-Service-Token", serviceToken).build();
    }
}
