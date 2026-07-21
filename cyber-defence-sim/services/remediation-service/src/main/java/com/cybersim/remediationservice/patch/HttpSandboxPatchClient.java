package com.cybersim.remediationservice.patch;

import com.cybersim.remediationservice.model.RemediationType;
import com.cybersim.shared.security.ServiceJwtSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class HttpSandboxPatchClient implements SandboxPatchClient {
    private final RestClient restClient;

    HttpSandboxPatchClient(
            @Value("${remediation.target-system-base-url}") String baseUrl,
            @Value("${service-jwt.secret}") String serviceJwtSecret,
            @Value("${service-jwt.issuer}") String serviceJwtIssuer
    ) {
        ServiceJwtSupport.TokenIssuer tokenIssuer = ServiceJwtSupport.issuer(serviceJwtSecret, serviceJwtIssuer,
                "remediation-service", "SERVICE_REMEDIATION", "target-system-service");
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenIssuer.issue());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Override
    public PatchExecutionResult apply(RemediationType remediationType) {
        return execute("/internal/patches/{patchName}", remediationType, "Patch applied to sandbox target");
    }

    @Override
    public PatchExecutionResult rollback(RemediationType remediationType) {
        return execute("/internal/patches/{patchName}/rollback", remediationType,
                "Patch rolled back on sandbox target");
    }

    private PatchExecutionResult execute(String path, RemediationType remediationType, String successSummary) {
        return execute(restClient, path, remediationType, successSummary);
    }

    private PatchExecutionResult execute(RestClient client, String path, RemediationType remediationType,
                                         String successSummary) {
        try {
            client.post().uri(path, remediationType.patchName()).retrieve().toBodilessEntity();
            return PatchExecutionResult.success(successSummary);
        } catch (RestClientResponseException exception) {
            return PatchExecutionResult.failure("Target patch request failed with HTTP " + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            return PatchExecutionResult.failure("Target patch service is unavailable");
        }
    }
}
