package com.cybersim.verificationservice.controller;

import com.cybersim.shared.dto.RemediationResponse;
import com.cybersim.shared.dto.VerificationCreateRequest;
import com.cybersim.shared.dto.VerificationResponse;
import com.cybersim.shared.observability.ApiErrors;
import com.cybersim.verificationservice.model.VerificationRecord;
import com.cybersim.verificationservice.store.VerificationStore;
import com.cybersim.verificationservice.workflow.VerificationCheckResult;
import com.cybersim.verificationservice.workflow.VerificationWorkflowClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

@RestController
public class VerificationController {
    private final VerificationStore store;
    private final VerificationWorkflowClient workflowClient;

    public VerificationController(VerificationStore store, VerificationWorkflowClient workflowClient) {
        this.store = store;
        this.workflowClient = workflowClient;
    }

    @PostMapping("/verifications")
    public ResponseEntity<Object> verify(
            @Valid @RequestBody VerificationCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey
    ) {
        if (idempotencyKey != null) {
            VerificationRecord existing = store.findById(idempotencyKey).orElse(null);
            if (existing != null) {
                if (!existing.remediationId().equals(request.remediationId())) {
                    throw new com.cybersim.shared.exceptions.ConflictException(
                            "Idempotency key already belongs to a different verification");
                }
                return ResponseEntity.ok(existing.toResponse());
            }
        }
        RemediationResponse remediation = workflowClient.findRemediation(request.remediationId()).orElse(null);
        if (remediation == null) {
            return ApiErrors.response(HttpStatus.NOT_FOUND, "Remediation not found or unavailable", "/verifications");
        }
        boolean failedVerification = "FAILED".equals(remediation.status()) && remediation.appliedAt() != null;
        if (!"APPLIED".equals(remediation.status()) && !"VERIFIED".equals(remediation.status()) && !failedVerification) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Only an applied remediation can be verified", "/verifications");
        }

        VerificationCheckResult result = workflowClient.verifyPatch(remediation);
        if (!workflowClient.synchronizeOutcome(remediation, result)) {
            result = result.withPendingSynchronization();
        }
        UUID verificationId = idempotencyKey == null ? UUID.randomUUID() : idempotencyKey;
        VerificationRecord saved = store.save(VerificationRecord.from(remediation, result, verificationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse());
    }

    public ResponseEntity<Object> verify(VerificationCreateRequest request) {
        return verify(request, null);
    }

    @GetMapping("/verifications/{id}")
    public ResponseEntity<Object> get(@PathVariable UUID id) {
        return store.findById(id)
                .<ResponseEntity<Object>>map(result -> ResponseEntity.ok(result.toResponse()))
                .orElseGet(() -> ApiErrors.response(HttpStatus.NOT_FOUND, "Verification result not found", "/verifications/" + id));
    }

    @GetMapping("/simulations/{id}/verifications")
    public List<VerificationResponse> list(@PathVariable UUID id) {
        return store.findBySimulationId(id).stream().map(VerificationRecord::toResponse).toList();
    }
}
