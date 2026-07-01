package com.cybersim.targetregistryservice.controller;

import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.dto.TargetRegistrationRequest;
import com.cybersim.shared.dto.TargetResponse;
import com.cybersim.shared.observability.ApiErrors;
import com.cybersim.shared.validation.TargetScopeValidator;
import com.cybersim.targetregistryservice.model.TargetRecord;
import com.cybersim.targetregistryservice.store.TargetStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class TargetRegistryController {
    private final TargetStore targetStore;

    public TargetRegistryController(TargetStore targetStore) {
        this.targetStore = targetStore;
    }

    @PostMapping({"/targets", "/integration/targets"})
    public ResponseEntity<Object> create(@Valid @RequestBody TargetRegistrationRequest request) {
        var scopeViolation = TargetScopeValidator.findViolation(request);
        if (scopeViolation.isPresent()) {
            return error(HttpStatus.BAD_REQUEST, "Unsafe target scope: " + scopeViolation.get(), "/targets");
        }
        boolean internalSandbox = request.mode() == TargetMode.INTERNAL_SANDBOX;
        boolean productionEnvironment = isProductionEnvironment(request.environmentType());
        boolean authorizedStaging = request.mode() == TargetMode.EXTERNAL_STAGING_TARGET
                && request.writtenAuthorizationConfirmed()
                && !productionEnvironment;
        String status = internalSandbox ? "ACTIVE" : "PENDING_VERIFICATION";
        String verification = internalSandbox ? "VERIFIED" : "PENDING";
        if (!internalSandbox && !authorizedStaging) {
            status = "DISABLED";
            verification = "FAILED";
        }
        TargetRecord stored = targetStore.save(TargetRecord.from(request, verification, status));
        return ResponseEntity.status(201).body(stored.toResponse());
    }

    @GetMapping({"/targets", "/integration/targets"})
    public List<TargetResponse> list() {
        return targetStore.findAll().stream().map(TargetRecord::toResponse).toList();
    }

    @GetMapping({"/targets/{id}", "/integration/targets/{id}"})
    public ResponseEntity<Object> get(@PathVariable UUID id) {
        return targetStore.findById(id)
                .<ResponseEntity<Object>>map(target -> ResponseEntity.ok(target.toResponse()))
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, "Target not found: " + id, "/targets/" + id));
    }

    @PostMapping({"/targets/{id}/verify-ownership", "/integration/targets/{id}/verify-ownership"})
    public ResponseEntity<Object> verify(@PathVariable UUID id) {
        TargetRecord target = targetStore.findById(id).orElse(null);
        if (target == null) {
            return error(HttpStatus.NOT_FOUND, "Target not found: " + id, "/targets/" + id + "/verify-ownership");
        }
        if ("FAILED".equalsIgnoreCase(target.ownershipVerificationStatus()) || "DISABLED".equalsIgnoreCase(target.status())) {
            return error(HttpStatus.BAD_REQUEST, "Target is disabled or failed ownership verification", "/targets/" + id + "/verify-ownership");
        }
        if (isProductionEnvironment(target.environmentType())) {
            return error(HttpStatus.BAD_REQUEST, "Production targets are blocked by default", "/targets/" + id + "/verify-ownership");
        }
        TargetRecord verified = targetStore.save(target.withState("VERIFIED", "ACTIVE"));
        return ResponseEntity.ok(verified.toResponse());
    }

    @PostMapping("/targets/{id}/disable")
    public ResponseEntity<Object> disable(@PathVariable UUID id) {
        TargetRecord target = targetStore.findById(id).orElse(null);
        if (target == null) {
            return error(HttpStatus.NOT_FOUND, "Target not found: " + id, "/targets/" + id + "/disable");
        }
        TargetRecord disabled = targetStore.save(target.withState(target.ownershipVerificationStatus(), "DISABLED"));
        return ResponseEntity.ok(disabled.toResponse());
    }

    private ResponseEntity<Object> error(HttpStatus status, String message, String path) {
        return ApiErrors.response(status, message, path);
    }

    private boolean isProductionEnvironment(String environmentType) {
        return "PRODUCTION".equalsIgnoreCase(environmentType)
                || "PRODUCTION_BLOCKED".equalsIgnoreCase(environmentType);
    }
}
