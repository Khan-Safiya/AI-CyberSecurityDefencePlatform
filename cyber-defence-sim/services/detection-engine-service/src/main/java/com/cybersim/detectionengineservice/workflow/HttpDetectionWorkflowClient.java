package com.cybersim.detectionengineservice.workflow;

import com.cybersim.shared.dto.VulnerabilityResponse;
import com.cybersim.shared.security.ServiceJwtSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
class HttpDetectionWorkflowClient implements DetectionWorkflowClient {
    private final RestClient vulnerabilityClient;
    private final RestClient simulationClient;

    HttpDetectionWorkflowClient(
            @Value("${detection.vulnerability-base-url}") String vulnerabilityUrl,
            @Value("${detection.simulation-base-url}") String simulationUrl,
            @Value("${service-jwt.secret}") String serviceJwtSecret,
            @Value("${service-jwt.issuer}") String serviceJwtIssuer
    ) {
        vulnerabilityClient = RestClient.builder().baseUrl(vulnerabilityUrl).build();
        ServiceJwtSupport.TokenIssuer tokenIssuer = ServiceJwtSupport.issuer(serviceJwtSecret, serviceJwtIssuer,
                "detection-engine-service", "SERVICE_DETECTION", "simulation-orchestrator-service");
        simulationClient = RestClient.builder().baseUrl(simulationUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenIssuer.issue());
                    return execution.execute(request, body);
                }).build();
    }

    @Override
    public List<VulnerabilityResponse> findings(UUID simulationId) {
        List<VulnerabilityResponse> findings = vulnerabilityClient.get()
                .uri("/simulations/{id}/vulnerabilities", simulationId)
                .retrieve().body(new ParameterizedTypeReference<>() { });
        return findings == null ? List.of() : findings;
    }

    @Override
    public void completeDetectionStage(UUID simulationId, UUID roundId) {
        simulationClient.post().uri("/simulations/{simulationId}/rounds/{roundId}/detection-complete",
                simulationId, roundId).retrieve().toBodilessEntity();
    }

}
