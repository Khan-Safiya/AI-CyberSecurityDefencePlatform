package com.cybersim.remediationservice.controller;

import com.cybersim.remediationservice.store.RemediationStore;
import com.cybersim.shared.dto.VerificationOutcomeRequest;
import com.cybersim.shared.observability.ApiErrors;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class RemediationVerificationController {
    private final RemediationStore store;
    public RemediationVerificationController(RemediationStore store) {
        this.store = store;
    }

    @PostMapping("/internal/remediations/{id}/verification-result")
    public ResponseEntity<Object> recordVerification(
            @PathVariable UUID id,
            @Valid @RequestBody VerificationOutcomeRequest request
    ) {
        String path = "/internal/remediations/" + id + "/verification-result";
        return store.findById(id).map(remediation -> {
            boolean previouslyApplied = remediation.appliedAt() != null && remediation.rolledBackAt() == null;
            if (!previouslyApplied) {
                return ApiErrors.response(HttpStatus.CONFLICT, "Only an applied remediation can receive verification", path);
            }
            return ResponseEntity.ok((Object) store.save(remediation
                    .withVerificationOutcome(request.status(), request.evidenceSummary())).toResponse());
        }).orElseGet(() -> ApiErrors.response(HttpStatus.NOT_FOUND, "Remediation not found", path));
    }

}
