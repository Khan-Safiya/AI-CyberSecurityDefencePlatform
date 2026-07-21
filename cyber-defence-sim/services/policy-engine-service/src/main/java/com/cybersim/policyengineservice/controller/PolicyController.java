package com.cybersim.policyengineservice.controller;

import com.cybersim.policyengineservice.client.TargetScopeClient;
import com.cybersim.shared.dto.PolicyDecisionResponse;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.dto.TargetResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/policies")
public class PolicyController {
    private static final List<String> ALLOWED_ACTIONS = List.of(
            "SIMULATED_ENDPOINT_DISCOVERY",
            "SIMULATED_AUTH_REQUIRED_CHECK",
            "SIMULATED_ACCESS_CONTROL_CHECK",
            "SIMULATED_INPUT_VALIDATION_CHECK",
            "SIMULATED_RATE_LIMIT_CHECK",
            "SIMULATED_CONFIG_EXPOSURE_CHECK",
            "SIMULATED_DEPENDENCY_RISK_CHECK",
            "SIMULATED_EXTERNAL_AUTH_REQUIRED_CHECK",
            "SIMULATED_EXTERNAL_RATE_LIMIT_CHECK",
            "SIMULATED_EXTERNAL_CONFIG_EXPOSURE_CHECK",
            "SIMULATED_EXTERNAL_SECURITY_HEADERS_CHECK",
            "TRIAGE_FINDING",
            "CREATE_DETECTION_RULE",
            "RECOMMEND_REMEDIATION",
            "APPLY_REMEDIATION",
            "VERIFY_REMEDIATION"
    );

    private final TargetScopeClient targetScopeClient;

    public PolicyController(TargetScopeClient targetScopeClient) {
        this.targetScopeClient = targetScopeClient;
    }

    @PostMapping("/evaluate-action")
    public PolicyDecisionResponse evaluate(@Valid @RequestBody PolicyEvaluationRequest request) {
        if (request == null) {
            return new PolicyDecisionResponse(UUID.randomUUID(), null, null, null, null, false,
                    "Denied by default policy: missing evaluation request", Instant.now(), "baseline-safe-policy-v1");
        }
        String method = request.httpMethod() == null ? "GET" : request.httpMethod().toUpperCase();
        boolean safeMethod = List.of("GET", "POST", "PATCH").contains(method);
        boolean allowedAction = ALLOWED_ACTIONS.contains(request.actionType());
        boolean hasHost = request.host() != null && !request.host().isBlank();
        boolean sandboxPath = request.path() != null
                && (request.path().startsWith("/demo/") || request.path().startsWith("/internal/patches/"));

        boolean allowed;
        String reason;
        if (!safeMethod || !allowedAction || !hasHost) {
            allowed = false;
            reason = "Denied by default policy: action, method, or host is outside safe scope";
        } else if (sandboxPath) {
            allowed = true;
            reason = "Approved safe simulated action within declared scope";
        } else {
            Optional<String> scopeViolation = findExternalScopeViolation(request, method);
            allowed = scopeViolation.isEmpty();
            reason = allowed
                    ? "Approved safe simulated action within declared external target scope"
                    : "Denied by default policy: " + scopeViolation.get();
        }
        return new PolicyDecisionResponse(UUID.randomUUID(), request.simulationId(), request.targetId(),
                request.agentId(), request.actionType(), allowed, reason, Instant.now(), "baseline-safe-policy-v1");
    }

    private Optional<String> findExternalScopeViolation(PolicyEvaluationRequest request, String method) {
        Optional<TargetResponse> maybeTarget = targetScopeClient.targetScope(request.targetId());
        if (maybeTarget.isEmpty()) {
            return Optional.of("target is not registered or is not visible to the policy engine");
        }
        TargetResponse target = maybeTarget.get();
        if (target.mode() != TargetMode.EXTERNAL_STAGING_TARGET) {
            return Optional.of("action is outside the built-in sandbox scope and the target is not an external staging target");
        }
        if (!"ACTIVE".equalsIgnoreCase(target.status())) {
            return Optional.of("target is not active");
        }
        String requestHostname = request.host().split(":", 2)[0].toLowerCase(Locale.ROOT);
        boolean hostAllowed = target.allowedHosts().stream()
                .anyMatch(allowed -> allowed.split(":", 2)[0].equalsIgnoreCase(requestHostname));
        if (!hostAllowed) {
            return Optional.of("host is outside the target's declared allowed hosts");
        }
        if (!target.allowedHttpMethods().stream().anyMatch(allowed -> allowed.equalsIgnoreCase(method))) {
            return Optional.of("HTTP method is outside the target's declared allowed methods");
        }
        boolean pathExcluded = target.excludedPaths() != null
                && target.excludedPaths().stream().anyMatch(excluded -> request.path().startsWith(excluded));
        if (pathExcluded) {
            return Optional.of("path is within the target's declared excluded paths");
        }
        boolean pathAllowed = target.allowedPaths().stream().anyMatch(allowed -> request.path().startsWith(allowed));
        if (!pathAllowed) {
            return Optional.of("path is outside the target's declared allowed paths");
        }
        return Optional.empty();
    }

    @GetMapping
    public Map<String, Object> policies() {
        return Map.of(
                "version", "baseline-safe-policy-v1",
                "defaultDecision", "DENY",
                "allowedActions", ALLOWED_ACTIONS,
                "blockedBehaviors", List.of("real exploit payloads", "arbitrary scanning", "shell execution", "credential theft", "destructive tests")
        );
    }
}
