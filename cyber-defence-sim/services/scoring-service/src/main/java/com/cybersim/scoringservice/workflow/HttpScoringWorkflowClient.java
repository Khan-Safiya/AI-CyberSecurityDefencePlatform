package com.cybersim.scoringservice.workflow;

import com.cybersim.shared.dto.*;
import com.cybersim.shared.security.ServiceJwtSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
class HttpScoringWorkflowClient implements ScoringWorkflowClient {
    private final RestClient simulation, vulnerability, detection, remediation, verification;

    HttpScoringWorkflowClient(
            @Value("${scoring.simulation-base-url}") String simulationUrl,
            @Value("${scoring.vulnerability-base-url}") String vulnerabilityUrl,
            @Value("${scoring.detection-base-url}") String detectionUrl,
            @Value("${scoring.remediation-base-url}") String remediationUrl,
            @Value("${scoring.verification-base-url}") String verificationUrl,
            @Value("${service-jwt.secret}") String serviceJwtSecret,
            @Value("${service-jwt.issuer}") String serviceJwtIssuer
    ) {
        ServiceJwtSupport.TokenIssuer tokenIssuer = ServiceJwtSupport.issuer(serviceJwtSecret, serviceJwtIssuer,
                "scoring-service", "SERVICE_SCORING", "simulation-orchestrator-service");
        simulation = RestClient.builder().baseUrl(simulationUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenIssuer.issue());
                    return execution.execute(request, body);
                }).build();
        vulnerability = client(vulnerabilityUrl);
        detection = client(detectionUrl);
        remediation = client(remediationUrl);
        verification = client(verificationUrl);
    }

    public SimulationResponse simulation(UUID id){return simulation.get().uri("/simulations/{id}",id).retrieve().body(SimulationResponse.class);}
    public List<VulnerabilityResponse> findings(UUID id){List<VulnerabilityResponse> v=vulnerability.get().uri("/simulations/{id}/vulnerabilities",id).retrieve().body(new ParameterizedTypeReference<>(){});return v==null?List.of():v;}
    public List<DetectionEventResponse> detections(UUID id){List<DetectionEventResponse> v=detection.get().uri("/simulations/{id}/detections",id).retrieve().body(new ParameterizedTypeReference<>(){});return v==null?List.of():v;}
    public List<RemediationResponse> remediations(UUID id){List<RemediationResponse> v=remediation.get().uri("/simulations/{id}/remediations",id).retrieve().body(new ParameterizedTypeReference<>(){});return v==null?List.of():v;}
    public List<VerificationResponse> verifications(UUID id){List<VerificationResponse> v=verification.get().uri("/simulations/{id}/verifications",id).retrieve().body(new ParameterizedTypeReference<>(){});return v==null?List.of():v;}
    public void completeRound(UUID sid,UUID rid,RoundCompletionRequest body){simulation.post().uri("/simulations/{sid}/rounds/{rid}/complete",sid,rid).body(body).retrieve().toBodilessEntity();}
    private static RestClient client(String url){return RestClient.builder().baseUrl(url).build();}
}
