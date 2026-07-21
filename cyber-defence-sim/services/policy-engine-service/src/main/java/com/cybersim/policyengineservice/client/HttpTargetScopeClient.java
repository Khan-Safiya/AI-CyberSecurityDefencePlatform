package com.cybersim.policyengineservice.client;

import com.cybersim.shared.dto.TargetResponse;
import com.cybersim.shared.security.ServiceJwtSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;
import java.util.UUID;

@Component
class HttpTargetScopeClient implements TargetScopeClient {
    private final RestClient restClient;

    HttpTargetScopeClient(
            @Value("${policy.target-registry-base-url}") String targetRegistryBaseUrl,
            @Value("${service-jwt.secret}") String serviceJwtSecret,
            @Value("${service-jwt.issuer}") String serviceJwtIssuer
    ) {
        ServiceJwtSupport.TokenIssuer tokenIssuer = ServiceJwtSupport.issuer(serviceJwtSecret, serviceJwtIssuer,
                "policy-engine-service", "SERVICE_POLICY", "target-registry-service");
        this.restClient = RestClient.builder()
                .baseUrl(targetRegistryBaseUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenIssuer.issue());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Override
    public Optional<TargetResponse> targetScope(UUID targetId) {
        try {
            return Optional.ofNullable(restClient.get().uri("/internal/targets/{id}", targetId)
                    .retrieve().body(TargetResponse.class));
        } catch (RestClientResponseException exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }
}
