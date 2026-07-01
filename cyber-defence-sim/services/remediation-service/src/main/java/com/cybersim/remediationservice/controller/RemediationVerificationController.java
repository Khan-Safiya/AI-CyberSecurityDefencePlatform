package com.cybersim.remediationservice.controller;

import com.cybersim.remediationservice.store.RemediationStore;
import com.cybersim.shared.dto.VerificationOutcomeRequest;
import com.cybersim.shared.observability.ApiErrors;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class RemediationVerificationController {
    private final RemediationStore store;
    private final String expectedServiceToken;

    public RemediationVerificationController(
            RemediationStore store,
            @Value("${remediation.service-auth-token}") String expectedServiceToken
    ) {
        this.store = store;
        this.expectedServiceToken = expectedServiceToken;
    }

    @PostMapping("/internal/remediations/{id}/verification-result")
    public ResponseEntity<Object> recordVerification(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Service-Token", required = false) String serviceToken,
            @Valid @RequestBody VerificationOutcomeRequest request
    ) {
        String path = "/internal/remediations/" + id + "/verification-result";
        if (!validToken(serviceToken)) {
            return ApiErrors.response(HttpStatus.UNAUTHORIZED, "Valid service-to-service token required", path);
        }
        return store.findById(id).map(remediation -> {
            boolean previouslyApplied = remediation.appliedAt() != null && remediation.rolledBackAt() == null;
            if (!previouslyApplied) {
                return ApiErrors.response(HttpStatus.CONFLICT, "Only an applied remediation can receive verification", path);
            }
            return ResponseEntity.ok((Object) store.save(remediation
                    .withVerificationOutcome(request.status(), request.evidenceSummary())).toResponse());
        }).orElseGet(() -> ApiErrors.response(HttpStatus.NOT_FOUND, "Remediation not found", path));
    }

    private boolean validToken(String serviceToken) {
        return serviceToken != null && !serviceToken.isBlank() && serviceToken.equals(expectedServiceToken);
    }
}
