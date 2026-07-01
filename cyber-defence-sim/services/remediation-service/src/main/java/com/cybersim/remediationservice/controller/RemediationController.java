package com.cybersim.remediationservice.controller;

import com.cybersim.remediationservice.model.RemediationRecord;
import com.cybersim.remediationservice.patch.PatchExecutionResult;
import com.cybersim.remediationservice.patch.SandboxPatchClient;
import com.cybersim.remediationservice.store.RemediationStore;
import com.cybersim.shared.dto.RemediationCreateRequest;
import com.cybersim.shared.dto.RemediationResponse;
import com.cybersim.shared.observability.ApiErrors;
import com.cybersim.shared.exceptions.ConflictException;
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
public class RemediationController {
    private static final UUID SANDBOX_TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private final RemediationStore store;
    private final SandboxPatchClient patchClient;

    public RemediationController(RemediationStore store, SandboxPatchClient patchClient) {
        this.store = store;
        this.patchClient = patchClient;
    }

    @PostMapping("/remediations")
    public ResponseEntity<RemediationResponse> create(
            @Valid @RequestBody RemediationCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey
    ) {
        if (idempotencyKey != null) {
            RemediationRecord existing = store.findById(idempotencyKey).orElse(null);
            if (existing != null) {
                if (!existing.sameProposal(request)) {
                    throw new ConflictException("Idempotency key already belongs to a different remediation");
                }
                return ResponseEntity.ok(existing.toResponse());
            }
        }
        UUID remediationId = idempotencyKey == null ? UUID.randomUUID() : idempotencyKey;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(store.save(RemediationRecord.from(request, remediationId)).toResponse());
    }

    public ResponseEntity<RemediationResponse> create(RemediationCreateRequest request) {
        return create(request, null);
    }

    @GetMapping("/remediations/{id}")
    public ResponseEntity<Object> get(@PathVariable UUID id) {
        return store.findById(id)
                .<ResponseEntity<Object>>map(remediation -> ResponseEntity.ok(remediation.toResponse()))
                .orElseGet(() -> notFound(id));
    }

    @GetMapping("/simulations/{id}/remediations")
    public List<RemediationResponse> list(@PathVariable UUID id) {
        return store.findBySimulationId(id).stream().map(RemediationRecord::toResponse).toList();
    }

    @PostMapping("/remediations/{id}/approve")
    public ResponseEntity<Object> approve(@PathVariable UUID id) {
        return store.findById(id).map(remediation -> {
            if (!"PROPOSED".equals(remediation.status())) {
                return conflict(id, "approve", "Only a proposed remediation can be approved");
            }
            return ResponseEntity.ok((Object) store.save(remediation.approve()).toResponse());
        }).orElseGet(() -> notFound(id));
    }

    @PostMapping("/remediations/{id}/apply")
    public ResponseEntity<Object> apply(@PathVariable UUID id) {
        return store.findById(id).map(remediation -> {
            boolean retryableFailure = "FAILED".equals(remediation.status()) && remediation.appliedAt() == null;
            if (!"APPROVED".equals(remediation.status()) && !retryableFailure) {
                return conflict(id, "apply", "Only an approved remediation or failed application can be applied");
            }
            if (!SANDBOX_TARGET_ID.equals(remediation.targetId())) {
                return conflict(id, "apply", "Automated patch application is limited to the built-in sandbox target");
            }
            PatchExecutionResult result = patchClient.apply(remediation.remediationType());
            return ResponseEntity.ok((Object) store.save(remediation.applied(result.successful(), result.summary())).toResponse());
        }).orElseGet(() -> notFound(id));
    }

    @PostMapping("/remediations/{id}/rollback")
    public ResponseEntity<Object> rollback(@PathVariable UUID id) {
        return store.findById(id).map(remediation -> {
            boolean retryableFailure = "FAILED".equals(remediation.status()) && remediation.appliedAt() != null;
            if (!"APPLIED".equals(remediation.status()) && !retryableFailure) {
                return conflict(id, "rollback", "Only an applied remediation or failed rollback can be rolled back");
            }
            if (!SANDBOX_TARGET_ID.equals(remediation.targetId())) {
                return conflict(id, "rollback", "Automated patch rollback is limited to the built-in sandbox target");
            }
            PatchExecutionResult result = patchClient.rollback(remediation.remediationType());
            return ResponseEntity.ok((Object) store.save(remediation.rolledBack(result.successful(), result.summary())).toResponse());
        }).orElseGet(() -> notFound(id));
    }

    private ResponseEntity<Object> notFound(UUID id) {
        return ApiErrors.response(HttpStatus.NOT_FOUND, "Remediation not found", "/remediations/" + id);
    }

    private ResponseEntity<Object> conflict(UUID id, String action, String message) {
        return ApiErrors.response(HttpStatus.CONFLICT, message, "/remediations/" + id + "/" + action);
    }
}
