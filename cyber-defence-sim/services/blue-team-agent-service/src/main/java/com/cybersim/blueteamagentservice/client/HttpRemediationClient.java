package com.cybersim.blueteamagentservice.client;

import com.cybersim.shared.dto.RemediationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

@Component
class HttpRemediationClient implements RemediationClient {
    private final RestClient restClient;

    HttpRemediationClient(@Value("${blue-team.remediation-base-url}") String remediationBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(remediationBaseUrl).build();
    }

    @Override
    public List<RemediationResponse> forSimulation(UUID simulationId) {
        try {
            List<RemediationResponse> response = restClient.get().uri("/simulations/{id}/remediations", simulationId)
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            return response == null ? List.of() : response;
        } catch (RestClientException exception) {
            return List.of();
        }
    }
}
