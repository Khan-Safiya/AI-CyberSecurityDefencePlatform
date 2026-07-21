package com.cybersim.redteamagentservice.workflow;

import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.SimulationResponse;
import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.dto.TargetResponse;
import com.cybersim.shared.dto.VulnerabilityCreateRequest;
import com.cybersim.shared.http.SafeHttpResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedTeamStageProcessorTest {
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @Test
    void createsSixDeterministicFindingsAndCompletesStage() {
        FakeClient client = new FakeClient();
        RedTeamStageProcessor processor = new RedTeamStageProcessor(client);

        int count = processor.process(MESSAGE_ID, SIMULATION_ID, ROUND_ID);

        assertThat(count).isEqualTo(6);
        assertThat(client.policyRequests).hasSize(6).allSatisfy(request -> {
            assertThat(request.targetId()).isEqualTo(RedTeamStageProcessor.SANDBOX_TARGET_ID);
            assertThat(request.host()).isEqualTo("target-system-service");
        });
        assertThat(client.findingIds).hasSize(6).doesNotHaveDuplicates();
        assertThat(client.findings).extracting(VulnerabilityCreateRequest::type)
                .containsExactlyInAnyOrder("AUTHENTICATION", "ACCESS_CONTROL", "RATE_LIMIT",
                        "CONFIG_EXPOSURE", "INPUT_VALIDATION", "DEPENDENCY_RISK");
        assertThat(client.completed).isTrue();
    }

    @Test
    void policyDenialStopsBeforeStageCompletion() {
        FakeClient client = new FakeClient();
        client.allow = false;

        assertThatThrownBy(() -> new RedTeamStageProcessor(client).process(MESSAGE_ID, SIMULATION_ID, ROUND_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Policy denied");
        assertThat(client.findings).isEmpty();
        assertThat(client.completed).isFalse();
    }

    @Test
    void externalChecksDisabledByDefaultRejectsNonSandboxTarget() {
        UUID targetId = UUID.randomUUID();
        FakeClient client = new FakeClient() {
            @Override
            public SimulationResponse simulation(UUID simulationId) {
                return new SimulationResponse(simulationId, "Test", TargetMode.EXTERNAL_STAGING_TARGET,
                        targetId, "RUNNING", 1, 3, 60, 2, "LOW", true, 0, 0, 0, 50, null, List.of(), Instant.now(), null);
            }
        };

        assertThatThrownBy(() -> new RedTeamStageProcessor(client).process(MESSAGE_ID, SIMULATION_ID, ROUND_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("restricted to the built-in sandbox");
    }

    @Test
    void externalChecksEnabledRejectsInactiveTarget() {
        UUID targetId = UUID.randomUUID();
        FakeClient client = new FakeClient() {
            @Override
            public SimulationResponse simulation(UUID simulationId) {
                return new SimulationResponse(simulationId, "Test", TargetMode.EXTERNAL_STAGING_TARGET,
                        targetId, "RUNNING", 1, 3, 60, 2, "LOW", true, 0, 0, 0, 50, null, List.of(), Instant.now(), null);
            }

            @Override
            public Optional<TargetResponse> target(UUID id) {
                return Optional.of(new TargetResponse(id, "Staging", TargetMode.EXTERNAL_STAGING_TARGET,
                        "https://staging.example.com", "STAGING", List.of("staging.example.com"), List.of("/api"),
                        List.of(), List.of("GET"), "PENDING", "PENDING_VERIFICATION", "token", Instant.now()));
            }
        };
        ExternalCheckStrategy strategy = new ExternalCheckStrategy(request -> {
            throw new AssertionError("should not probe an inactive target");
        });
        RedTeamStageProcessor processor = new RedTeamStageProcessor(client, strategy, true);

        assertThatThrownBy(() -> processor.process(MESSAGE_ID, SIMULATION_ID, ROUND_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void externalChecksEnabledDelegatesToExternalStrategyForActiveTarget() {
        UUID targetId = UUID.randomUUID();
        FakeClient client = new FakeClient() {
            @Override
            public SimulationResponse simulation(UUID simulationId) {
                return new SimulationResponse(simulationId, "Test", TargetMode.EXTERNAL_STAGING_TARGET,
                        targetId, "RUNNING", 1, 3, 60, 2, "LOW", true, 0, 0, 0, 50, null, List.of(), Instant.now(), null);
            }

            @Override
            public Optional<TargetResponse> target(UUID id) {
                return Optional.of(new TargetResponse(id, "Staging", TargetMode.EXTERNAL_STAGING_TARGET,
                        "https://staging.example.com", "STAGING", List.of("staging.example.com"), List.of("/api"),
                        List.of(), List.of("GET"), "VERIFIED", "ACTIVE", "token", Instant.now()));
            }
        };
        ExternalCheckStrategy strategy = new ExternalCheckStrategy(request ->
                new SafeHttpResponse(429, Map.of("Strict-Transport-Security", List.of("max-age=1"),
                        "X-Content-Type-Options", List.of("nosniff")), new byte[0]));
        RedTeamStageProcessor processor = new RedTeamStageProcessor(client, strategy, true);

        int findings = processor.process(MESSAGE_ID, SIMULATION_ID, ROUND_ID);

        assertThat(findings).isZero();
        assertThat(client.completed).isTrue();
    }

    private static class FakeClient implements RedTeamWorkflowClient {
        private final List<PolicyEvaluationRequest> policyRequests = new ArrayList<>();
        private final List<UUID> findingIds = new ArrayList<>();
        private final List<VulnerabilityCreateRequest> findings = new ArrayList<>();
        private boolean allow = true;
        private boolean completed;

        @Override
        public SimulationResponse simulation(UUID simulationId) {
            return new SimulationResponse(simulationId, "Test", TargetMode.INTERNAL_SANDBOX,
                    RedTeamStageProcessor.SANDBOX_TARGET_ID, "RUNNING", 1, 3, 60, 2, "LOW", true, 0,
                    0, 0, 50, null, List.of(), Instant.now(), null);
        }

        @Override
        public Map<String, Boolean> sandboxPatchStates() {
            Map<String, Boolean> states = new LinkedHashMap<>();
            states.put("auth-required", false);
            states.put("object-authorization", false);
            states.put("rate-limit", false);
            states.put("disable-debug-endpoint", false);
            states.put("input-validation", false);
            states.put("update-dependency-metadata", false);
            return states;
        }

        @Override
        public Optional<TargetResponse> target(UUID targetId) {
            return Optional.empty();
        }

        @Override
        public boolean policyAllows(PolicyEvaluationRequest request) {
            policyRequests.add(request);
            return allow;
        }

        @Override
        public void createFinding(UUID idempotencyKey, VulnerabilityCreateRequest request) {
            findingIds.add(idempotencyKey);
            findings.add(request);
        }

        @Override
        public void completeRedTeamStage(UUID simulationId, UUID roundId) {
            completed = true;
        }
    }
}
