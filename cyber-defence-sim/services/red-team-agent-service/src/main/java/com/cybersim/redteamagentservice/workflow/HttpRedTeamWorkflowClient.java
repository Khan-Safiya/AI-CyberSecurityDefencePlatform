package com.cybersim.redteamagentservice.workflow;

import com.cybersim.shared.dto.PolicyDecisionResponse;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.SimulationResponse;
import com.cybersim.shared.dto.VulnerabilityCreateRequest;
import com.cybersim.shared.security.ServiceJwtSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
class HttpRedTeamWorkflowClient implements RedTeamWorkflowClient {
    private final RestClient simulationClient;
    private final RestClient policyClient;
    private final RestClient vulnerabilityClient;
    private final RestClient targetClient;

    HttpRedTeamWorkflowClient(
            @Value("${red-team.simulation-base-url}") String simulationUrl,
            @Value("${red-team.policy-base-url}") String policyUrl,
            @Value("${red-team.vulnerability-base-url}") String vulnerabilityUrl,
            @Value("${red-team.target-base-url}") String targetUrl,
            @Value("${service-jwt.secret}") String serviceJwtSecret,
            @Value("${service-jwt.issuer}") String serviceJwtIssuer
    ) {
        ServiceJwtSupport.TokenIssuer tokenIssuer = ServiceJwtSupport.issuer(serviceJwtSecret, serviceJwtIssuer,
                "red-team-agent-service", "SERVICE_RED_TEAM", "simulation-orchestrator-service");
        simulationClient = RestClient.builder().baseUrl(simulationUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenIssuer.issue());
                    return execution.execute(request, body);
                }).build();
        policyClient = client(policyUrl);
        vulnerabilityClient = client(vulnerabilityUrl);
        ServiceJwtSupport.TokenIssuer targetTokenIssuer = ServiceJwtSupport.issuer(serviceJwtSecret, serviceJwtIssuer,
                "red-team-agent-service", "SERVICE_RED_TEAM", "target-system-service");
        targetClient = serviceJwtClient(targetUrl, targetTokenIssuer);
    }

    @Override
    public SimulationResponse simulation(UUID simulationId) {
        return simulationClient.get().uri("/simulations/{id}", simulationId).retrieve().body(SimulationResponse.class);
    }

    @Override
    public Map<String, Boolean> sandboxPatchStates() {
        return targetClient.get().uri("/internal/patches/status").retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    @Override
    public boolean policyAllows(PolicyEvaluationRequest request) {
        PolicyDecisionResponse response = policyClient.post().uri("/policies/evaluate-action")
                .body(request).retrieve().body(PolicyDecisionResponse.class);
        return response != null && response.allowed();
    }

    @Override
    public void createFinding(UUID idempotencyKey, VulnerabilityCreateRequest request) {
        vulnerabilityClient.post().uri("/vulnerabilities")
                .header("Idempotency-Key", idempotencyKey.toString())
                .body(request).retrieve().toBodilessEntity();
    }

    @Override
    public void completeRedTeamStage(UUID simulationId, UUID roundId) {
        simulationClient.post().uri("/simulations/{simulationId}/rounds/{roundId}/red-team-complete",
                simulationId, roundId).retrieve().toBodilessEntity();
    }

    private static RestClient client(String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    private static RestClient serviceJwtClient(String baseUrl, ServiceJwtSupport.TokenIssuer tokenIssuer) {
        return RestClient.builder().baseUrl(baseUrl).requestInterceptor((request, body, execution) -> {
            request.getHeaders().setBearerAuth(tokenIssuer.issue());
            return execution.execute(request, body);
        }).build();
    }
}
