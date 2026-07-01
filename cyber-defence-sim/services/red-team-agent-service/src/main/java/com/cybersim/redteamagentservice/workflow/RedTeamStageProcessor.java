package com.cybersim.redteamagentservice.workflow;

import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.SimulationResponse;
import com.cybersim.shared.dto.VulnerabilityCreateRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RedTeamStageProcessor {
    static final UUID SANDBOX_TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    static final UUID RED_TEAM_AGENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");

    private static final List<SafeCheck> CHECKS = List.of(
            new SafeCheck("auth-required", "SIMULATED_AUTH_REQUIRED_CHECK", "/demo/admin/report", "GET",
                    "Missing authentication", "AUTHENTICATION", "HIGH",
                    "The mock admin report is accessible without authentication.", "Require authentication."),
            new SafeCheck("object-authorization", "SIMULATED_ACCESS_CONTROL_CHECK", "/demo/users/demo-user/documents", "GET",
                    "Missing object-level authorization", "ACCESS_CONTROL", "HIGH",
                    "The mock document endpoint does not enforce ownership.", "Enforce owner checks."),
            new SafeCheck("rate-limit", "SIMULATED_RATE_LIMIT_CHECK", "/demo/login", "POST",
                    "Missing rate limit", "RATE_LIMIT", "MEDIUM",
                    "The mock login endpoint does not limit repeated attempts.", "Add rate limiting."),
            new SafeCheck("disable-debug-endpoint", "SIMULATED_CONFIG_EXPOSURE_CHECK", "/demo/debug/config", "GET",
                    "Exposed debug config", "CONFIG_EXPOSURE", "MEDIUM",
                    "The sandbox exposes mock debug configuration.", "Disable the debug endpoint."),
            new SafeCheck("input-validation", "SIMULATED_INPUT_VALIDATION_CHECK", "/demo/billing/search", "GET",
                    "Missing input validation", "INPUT_VALIDATION", "MEDIUM",
                    "The mock billing search accepts unchecked input.", "Validate query input."),
            new SafeCheck("update-dependency-metadata", "SIMULATED_DEPENDENCY_RISK_CHECK", "/demo/dependencies", "GET",
                    "Simulated dependency risk", "DEPENDENCY_RISK", "LOW",
                    "The sandbox declares intentionally outdated mock dependency metadata.",
                    "Update dependency metadata."));

    private final RedTeamWorkflowClient client;

    public RedTeamStageProcessor(RedTeamWorkflowClient client) {
        this.client = client;
    }

    public int process(UUID messageId, UUID simulationId, UUID roundId) {
        SimulationResponse simulation = client.simulation(simulationId);
        if (simulation == null || !SANDBOX_TARGET_ID.equals(simulation.targetId())) {
            throw new IllegalStateException("Automated red-team checks are restricted to the built-in sandbox target");
        }
        Map<String, Boolean> patchStates = client.sandboxPatchStates();
        if (patchStates == null) {
            throw new IllegalStateException("Sandbox patch state is unavailable");
        }

        int findings = 0;
        for (SafeCheck check : CHECKS) {
            Boolean patched = patchStates.get(check.patchName());
            if (patched == null) {
                throw new IllegalStateException("Sandbox did not report patch state: " + check.patchName());
            }
            if (patched) {
                continue;
            }
            PolicyEvaluationRequest policyRequest = new PolicyEvaluationRequest(simulationId, simulation.targetId(),
                    RED_TEAM_AGENT_ID, check.actionType(), "target-system-service", check.path(), check.method());
            if (!client.policyAllows(policyRequest)) {
                throw new IllegalStateException("Policy denied safe red-team action: " + check.actionType());
            }
            UUID findingId = UUID.nameUUIDFromBytes((messageId + ":" + check.type())
                    .getBytes(StandardCharsets.UTF_8));
            client.createFinding(findingId, new VulnerabilityCreateRequest(
                    simulationId, roundId, simulation.targetId(), check.title(), check.description(), check.type(),
                    check.severity(), "Allowlisted sandbox state reports this check as vulnerable.",
                    "Inspect only the built-in mock endpoint " + check.path() + ".", check.path(), check.expectedFix(),
                    RED_TEAM_AGENT_ID, null));
            findings++;
        }
        client.completeRedTeamStage(simulationId, roundId);
        return findings;
    }

    private record SafeCheck(
            String patchName,
            String actionType,
            String path,
            String method,
            String title,
            String type,
            String severity,
            String description,
            String expectedFix
    ) {
    }
}
