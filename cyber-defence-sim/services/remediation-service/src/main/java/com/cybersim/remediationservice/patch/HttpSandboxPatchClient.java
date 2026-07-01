package com.cybersim.remediationservice.patch;

import com.cybersim.remediationservice.model.RemediationType;
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
            @Value("${remediation.service-auth-token}") String serviceAuthToken
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Service-Token", serviceAuthToken)
                .build();
    }

    @Override
    public PatchExecutionResult apply(RemediationType remediationType) {
        return execute("/internal/patches/{patchName}", remediationType, "Patch applied to sandbox target");
    }

    @Override
    public PatchExecutionResult rollback(RemediationType remediationType) {
        return execute("/internal/patches/{patchName}/rollback", remediationType, "Patch rolled back on sandbox target");
    }

    private PatchExecutionResult execute(String path, RemediationType remediationType, String successSummary) {
        try {
            restClient.post().uri(path, remediationType.patchName()).retrieve().toBodilessEntity();
            return PatchExecutionResult.success(successSummary);
        } catch (RestClientResponseException exception) {
            return PatchExecutionResult.failure("Target patch request failed with HTTP " + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            return PatchExecutionResult.failure("Target patch service is unavailable");
        }
    }
}
