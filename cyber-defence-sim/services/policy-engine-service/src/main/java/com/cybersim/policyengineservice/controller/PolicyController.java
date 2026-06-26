package com.cybersim.policyengineservice.controller;

import com.cybersim.shared.dto.PolicyDecisionResponse;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
            "TRIAGE_FINDING",
            "CREATE_DETECTION_RULE",
            "RECOMMEND_REMEDIATION",
            "APPLY_REMEDIATION",
            "VERIFY_REMEDIATION"
    );

    @PostMapping("/evaluate-action")
    public PolicyDecisionResponse evaluate(@RequestBody PolicyEvaluationRequest request) {
        String method = request.httpMethod() == null ? "GET" : request.httpMethod().toUpperCase();
        boolean safeMethod = List.of("GET", "POST", "PATCH").contains(method);
        boolean allowedAction = ALLOWED_ACTIONS.contains(request.actionType());
        boolean scopedPath = request.path() != null && (request.path().startsWith("/demo/") || request.path().startsWith("/internal/patches/"));
        boolean allowed = safeMethod && allowedAction && scopedPath && request.host() != null && !request.host().isBlank();
        String reason = allowed
                ? "Approved safe simulated action within declared scope"
                : "Denied by default policy: action, method, host, or path is outside safe scope";
        return new PolicyDecisionResponse(UUID.randomUUID(), request.simulationId(), request.targetId(),
                request.agentId(), request.actionType(), allowed, reason, Instant.now(), "baseline-safe-policy-v1");
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
