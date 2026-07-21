package com.cybersim.policyengineservice.controller;

import com.cybersim.policyengineservice.client.TargetScopeClient;
import com.cybersim.shared.dto.PolicyDecisionResponse;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.dto.TargetResponse;
import com.cybersim.shared.exceptions.GlobalApiExceptionHandler;
import com.cybersim.shared.observability.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PolicyControllerTest {
    private PolicyController controller;
    private MockMvc mockMvc;
    private FakeTargetScopeClient targetScopeClient;

    @BeforeEach
    void setUp() {
        targetScopeClient = new FakeTargetScopeClient();
        controller = new PolicyController(targetScopeClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void emptyPolicyEvaluationIsDeniedByDefault() {
        PolicyDecisionResponse response = controller.evaluate(null);

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("missing evaluation request");
        assertThat(response.policyVersion()).isEqualTo("baseline-safe-policy-v1");
    }

    @Test
    void safeSandboxActionInsideScopeIsAllowed() {
        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                UUID.randomUUID(),
                "SIMULATED_AUTH_REQUIRED_CHECK",
                "target-system-service",
                "/demo/admin/report",
                "GET"
        ));

        assertThat(response.allowed()).isTrue();
    }

    @Test
    void unknownActionOutsideScopeIsDenied() {
        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "REAL_NETWORK_SCAN",
                "example.com",
                "/",
                "GET"
        ));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("Denied by default policy");
    }

    @Test
    void malformedPolicyRequestIsRejected() throws Exception {
        mockMvc.perform(post("/policies/evaluate-action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"SIMULATED_AUTH_REQUIRED_CHECK\",\"host\":\"bad host\",\"path\":\"demo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void externalActionWithinDeclaredScopeIsAllowed() {
        UUID targetId = UUID.randomUUID();
        targetScopeClient.targets.put(targetId, activeExternalTarget());

        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(), targetId, UUID.randomUUID(),
                "SIMULATED_EXTERNAL_AUTH_REQUIRED_CHECK", "staging.example.com", "/api/status", "GET"
        ));

        assertThat(response.allowed()).isTrue();
    }

    @Test
    void externalActionForUnregisteredTargetIsDenied() {
        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "SIMULATED_EXTERNAL_AUTH_REQUIRED_CHECK", "staging.example.com", "/api/status", "GET"
        ));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("not registered");
    }

    @Test
    void externalActionForInactiveTargetIsDenied() {
        UUID targetId = UUID.randomUUID();
        targetScopeClient.targets.put(targetId, new TargetResponse(targetId, "Staging", TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com", "STAGING", List.of("staging.example.com"), List.of("/api"),
                List.of(), List.of("GET"), "PENDING", "PENDING_VERIFICATION", "token", Instant.now()));

        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(), targetId, UUID.randomUUID(),
                "SIMULATED_EXTERNAL_AUTH_REQUIRED_CHECK", "staging.example.com", "/api/status", "GET"
        ));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("not active");
    }

    @Test
    void externalActionOutsideAllowedHostIsDenied() {
        UUID targetId = UUID.randomUUID();
        targetScopeClient.targets.put(targetId, activeExternalTarget());

        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(), targetId, UUID.randomUUID(),
                "SIMULATED_EXTERNAL_AUTH_REQUIRED_CHECK", "attacker.example.com", "/api/status", "GET"
        ));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("declared allowed hosts");
    }

    @Test
    void externalActionOutsideAllowedPathIsDenied() {
        UUID targetId = UUID.randomUUID();
        targetScopeClient.targets.put(targetId, activeExternalTarget());

        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(), targetId, UUID.randomUUID(),
                "SIMULATED_EXTERNAL_AUTH_REQUIRED_CHECK", "staging.example.com", "/admin/secret", "GET"
        ));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("declared allowed paths");
    }

    @Test
    void externalActionOnExcludedPathIsDenied() {
        UUID targetId = UUID.randomUUID();
        targetScopeClient.targets.put(targetId, new TargetResponse(targetId, "Staging", TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com", "STAGING", List.of("staging.example.com"), List.of("/api"),
                List.of("/api/internal"), List.of("GET"), "VERIFIED", "ACTIVE", "token", Instant.now()));

        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(), targetId, UUID.randomUUID(),
                "SIMULATED_EXTERNAL_AUTH_REQUIRED_CHECK", "staging.example.com", "/api/internal/debug", "GET"
        ));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("excluded paths");
    }

    @Test
    void externalActionOutsideAllowedMethodIsDenied() {
        UUID targetId = UUID.randomUUID();
        targetScopeClient.targets.put(targetId, activeExternalTarget());

        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(), targetId, UUID.randomUUID(),
                "SIMULATED_EXTERNAL_AUTH_REQUIRED_CHECK", "staging.example.com", "/api/status", "PATCH"
        ));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("declared allowed methods");
    }

    @Test
    void sandboxTargetCannotUseNonSandboxPathEvenIfRegistered() {
        UUID targetId = UUID.randomUUID();
        targetScopeClient.targets.put(targetId, new TargetResponse(targetId, "Sandbox", TargetMode.INTERNAL_SANDBOX,
                "http://target-system-service", "SANDBOX", List.of("target-system-service"), List.of("/"),
                List.of(), List.of("GET"), "VERIFIED", "ACTIVE", "token", Instant.now()));

        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(), targetId, UUID.randomUUID(),
                "SIMULATED_EXTERNAL_AUTH_REQUIRED_CHECK", "target-system-service", "/other/path", "GET"
        ));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("not an external staging target");
    }

    private static TargetResponse activeExternalTarget() {
        return new TargetResponse(UUID.randomUUID(), "Staging", TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com", "STAGING", List.of("staging.example.com"), List.of("/api"),
                List.of(), List.of("GET"), "VERIFIED", "ACTIVE", "token", Instant.now());
    }

    private static final class FakeTargetScopeClient implements TargetScopeClient {
        private final Map<UUID, TargetResponse> targets = new HashMap<>();

        @Override
        public Optional<TargetResponse> targetScope(UUID targetId) {
            return Optional.ofNullable(targets.get(targetId));
        }
    }
}
