package com.cybersim.remediationservice.workflow;

import com.cybersim.remediationservice.model.RemediationRecord;
import com.cybersim.remediationservice.model.RemediationType;
import com.cybersim.remediationservice.patch.PatchExecutionResult;
import com.cybersim.remediationservice.patch.SandboxPatchClient;
import com.cybersim.remediationservice.store.RemediationStore;
import com.cybersim.shared.dto.DetectionEventResponse;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.VulnerabilityResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemediationStageProcessorTest {
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID FINDING_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID DETECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");

    @Test
    void appliesAllowlistedCurrentRoundRemediationAndSkipsItOnRetry() {
        InMemoryStore store = new InMemoryStore();
        FakePatchClient patchClient = new FakePatchClient();
        FakeWorkflowClient workflow = new FakeWorkflowClient();
        RemediationStageProcessor processor = new RemediationStageProcessor(store, patchClient, workflow);

        RemediationStageProcessor.BatchResult first = processor.persistAndApply(MESSAGE_ID, SIMULATION_ID, ROUND_ID,
                List.of(finding()), List.of(detection()));
        RemediationStageProcessor.BatchResult retry = processor.persistAndApply(MESSAGE_ID, SIMULATION_ID, ROUND_ID,
                List.of(finding()), List.of(detection()));

        assertThat(first.successful()).isTrue();
        assertThat(first.appliedCount()).isEqualTo(1);
        assertThat(retry.appliedCount()).isZero();
        assertThat(patchClient.applyCount).isEqualTo(1);
        assertThat(store.actions).singleElement().satisfies(action -> {
            assertThat(action.status()).isEqualTo("APPLIED");
            assertThat(action.detectionId()).isEqualTo(DETECTION_ID);
        });
        assertThat(workflow.policyRequests).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo("/internal/patches/auth-required"));
    }

    @Test
    void failedPatchIsDurableAndCanBeRetried() {
        InMemoryStore store = new InMemoryStore();
        FakePatchClient patchClient = new FakePatchClient();
        patchClient.fail = true;
        RemediationStageProcessor processor = new RemediationStageProcessor(store, patchClient, new FakeWorkflowClient());

        RemediationStageProcessor.BatchResult failed = processor.persistAndApply(MESSAGE_ID, SIMULATION_ID, ROUND_ID,
                List.of(finding()), List.of(detection()));
        patchClient.fail = false;
        RemediationStageProcessor.BatchResult retried = processor.persistAndApply(MESSAGE_ID, SIMULATION_ID, ROUND_ID,
                List.of(finding()), List.of(detection()));

        assertThat(failed.successful()).isFalse();
        assertThat(retried.successful()).isTrue();
        assertThat(store.actions.getFirst().status()).isEqualTo("APPLIED");
    }

    @Test
    void refusesAutomatedRemediationForExternalTarget() {
        InMemoryStore store = new InMemoryStore();
        FakePatchClient patchClient = new FakePatchClient();
        RemediationStageProcessor processor = new RemediationStageProcessor(store, patchClient, new FakeWorkflowClient());
        VulnerabilityResponse external = new VulnerabilityResponse(FINDING_ID, SIMULATION_ID, ROUND_ID,
                UUID.randomUUID(), "Missing authentication", "Description", "AUTHENTICATION", "HIGH", "OPEN",
                "Evidence", "/demo/admin/report", "Safe steps", "Fix", null, null,
                Instant.now(), Instant.now(), null);

        assertThatThrownBy(() -> processor.persistAndApply(MESSAGE_ID, SIMULATION_ID, ROUND_ID,
                List.of(external), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("built-in sandbox");
        assertThat(patchClient.applyCount).isZero();
        assertThat(store.actions).isEmpty();
    }

    private VulnerabilityResponse finding() {
        return new VulnerabilityResponse(FINDING_ID, SIMULATION_ID, ROUND_ID, TARGET_ID, "Missing authentication",
                "Description", "AUTHENTICATION", "HIGH", "OPEN", "Evidence", "/demo/admin/report",
                "Safe steps", "Fix", null, null, Instant.now(), Instant.now(), null);
    }

    private DetectionEventResponse detection() {
        return new DetectionEventResponse(DETECTION_ID, SIMULATION_ID, ROUND_ID, TARGET_ID, "RED_TEAM_FINDING",
                "detection.created", "HIGH", "Observed", null, FINDING_ID, Map.of(), Instant.now());
    }

    private static final class InMemoryStore implements RemediationStore {
        private final List<RemediationRecord> actions = new ArrayList<>();
        public RemediationRecord save(RemediationRecord value) {
            actions.removeIf(existing -> existing.id().equals(value.id())); actions.add(value); return value;
        }
        public Optional<RemediationRecord> findById(UUID id) { return actions.stream().filter(x -> id.equals(x.id())).findFirst(); }
        public List<RemediationRecord> findBySimulationId(UUID id) { return actions.stream().filter(x -> id.equals(x.simulationId())).toList(); }
    }

    private static final class FakePatchClient implements SandboxPatchClient {
        private int applyCount;
        private boolean fail;
        public PatchExecutionResult apply(RemediationType type) {
            applyCount++; return fail ? PatchExecutionResult.failure("Unavailable") : PatchExecutionResult.success("Applied");
        }
        public PatchExecutionResult rollback(RemediationType type) { return PatchExecutionResult.success("Rolled back"); }
    }

    private static final class FakeWorkflowClient implements RemediationWorkflowClient {
        private final List<PolicyEvaluationRequest> policyRequests = new ArrayList<>();
        public List<VulnerabilityResponse> findings(UUID id) { return List.of(); }
        public List<DetectionEventResponse> detections(UUID id) { return List.of(); }
        public boolean policyAllows(PolicyEvaluationRequest request) { policyRequests.add(request); return true; }
        public void completeBlueTeamStage(UUID simulationId, UUID roundId) { }
    }
}
