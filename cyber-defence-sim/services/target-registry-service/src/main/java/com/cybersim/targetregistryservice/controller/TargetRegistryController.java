package com.cybersim.targetregistryservice.controller;

import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.dto.TargetRegistrationRequest;
import com.cybersim.shared.dto.TargetResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping
public class TargetRegistryController {
    private final Map<UUID, TargetResponse> targets = new ConcurrentHashMap<>();

    public TargetRegistryController() {
        TargetResponse sandbox = new TargetResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "Built-in sandbox target",
                TargetMode.INTERNAL_SANDBOX,
                "http://target-system-service:8080",
                "SANDBOX",
                List.of("target-system-service", "localhost"),
                List.of("/demo/**"),
                List.of("GET", "POST"),
                "VERIFIED",
                "ACTIVE",
                "sandbox-auto-verified",
                Instant.now()
        );
        targets.put(sandbox.id(), sandbox);
    }

    @PostMapping({"/targets", "/integration/targets"})
    public ResponseEntity<TargetResponse> create(@RequestBody TargetRegistrationRequest request) {
        UUID id = UUID.randomUUID();
        boolean internalSandbox = request.mode() == TargetMode.INTERNAL_SANDBOX;
        boolean authorizedStaging = request.mode() == TargetMode.EXTERNAL_STAGING_TARGET
                && request.writtenAuthorizationConfirmed()
                && !"PRODUCTION".equalsIgnoreCase(request.environmentType());
        String status = internalSandbox ? "ACTIVE" : "PENDING_VERIFICATION";
        String verification = internalSandbox ? "VERIFIED" : "PENDING";
        if (!internalSandbox && !authorizedStaging) {
            status = "DISABLED";
            verification = "FAILED";
        }
        TargetResponse response = new TargetResponse(
                id,
                request.name(),
                request.mode(),
                request.baseUrl(),
                request.environmentType(),
                request.allowedHosts() == null ? List.of() : request.allowedHosts(),
                request.allowedPaths() == null ? List.of() : request.allowedPaths(),
                request.allowedHttpMethods() == null ? List.of("GET") : request.allowedHttpMethods(),
                verification,
                status,
                UUID.randomUUID().toString(),
                Instant.now()
        );
        targets.put(id, response);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping({"/targets", "/integration/targets"})
    public List<TargetResponse> list() {
        return new ArrayList<>(targets.values());
    }

    @GetMapping({"/targets/{id}", "/integration/targets/{id}"})
    public ResponseEntity<TargetResponse> get(@PathVariable UUID id) {
        TargetResponse target = targets.get(id);
        return target == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(target);
    }

    @PostMapping({"/targets/{id}/verify-ownership", "/integration/targets/{id}/verify-ownership"})
    public ResponseEntity<TargetResponse> verify(@PathVariable UUID id) {
        TargetResponse target = targets.get(id);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        if ("PRODUCTION".equalsIgnoreCase(target.environmentType())) {
            return ResponseEntity.badRequest().build();
        }
        TargetResponse verified = new TargetResponse(target.id(), target.name(), target.mode(), target.baseUrl(),
                target.environmentType(), target.allowedHosts(), target.allowedPaths(), target.allowedHttpMethods(),
                "VERIFIED", "ACTIVE", target.verificationToken(), target.createdAt());
        targets.put(id, verified);
        return ResponseEntity.ok(verified);
    }

    @PostMapping("/targets/{id}/disable")
    public ResponseEntity<TargetResponse> disable(@PathVariable UUID id) {
        TargetResponse target = targets.get(id);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        TargetResponse disabled = new TargetResponse(target.id(), target.name(), target.mode(), target.baseUrl(),
                target.environmentType(), target.allowedHosts(), target.allowedPaths(), target.allowedHttpMethods(),
                target.ownershipVerificationStatus(), "DISABLED", target.verificationToken(), target.createdAt());
        targets.put(id, disabled);
        return ResponseEntity.ok(disabled);
    }
}
