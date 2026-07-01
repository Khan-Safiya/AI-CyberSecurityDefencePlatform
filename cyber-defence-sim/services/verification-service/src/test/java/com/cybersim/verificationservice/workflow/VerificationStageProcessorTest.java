package com.cybersim.verificationservice.workflow;

import com.cybersim.shared.dto.RemediationResponse;
import com.cybersim.verificationservice.model.VerificationRecord;
import com.cybersim.verificationservice.store.VerificationStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationStageProcessorTest {
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @Test
    void verifiesCurrentRoundRemediationOnce() {
        InMemoryStore store = new InMemoryStore();
        FakeWorkflow workflow = new FakeWorkflow();
        VerificationStageProcessor processor = new VerificationStageProcessor(store, workflow);

        int first = processor.verify(MESSAGE_ID, SIMULATION_ID, ROUND_ID, List.of(remediation()));
        int retry = processor.verify(MESSAGE_ID, SIMULATION_ID, ROUND_ID, List.of(remediation()));

        assertThat(first).isEqualTo(1);
        assertThat(retry).isZero();
        assertThat(workflow.verifyCalls).isEqualTo(1);
        assertThat(workflow.syncCalls).isEqualTo(1);
        assertThat(store.records).singleElement().satisfies(result -> assertThat(result.status()).isEqualTo("PASSED"));
    }

    @Test
    void synchronizationFailurePreventsVerificationCommit() {
        InMemoryStore store = new InMemoryStore();
        FakeWorkflow workflow = new FakeWorkflow();
        workflow.syncSuccessful = false;

        assertThatThrownBy(() -> new VerificationStageProcessor(store, workflow)
                .verify(MESSAGE_ID, SIMULATION_ID, ROUND_ID, List.of(remediation())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("synchronization failed");
        assertThat(store.records).isEmpty();
    }

    private RemediationResponse remediation() {
        Instant now = Instant.now();
        return new RemediationResponse(UUID.fromString("00000000-0000-0000-0000-000000000801"), SIMULATION_ID,
                ROUND_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000101"), "AUTH_REQUIRED", "Patch", "APPLIED",
                "Applied", now, now, now, now, null, null);
    }

    private static final class InMemoryStore implements VerificationStore {
        private final List<VerificationRecord> records = new ArrayList<>();
        public VerificationRecord save(VerificationRecord value) { records.add(value); return value; }
        public Optional<VerificationRecord> findById(UUID id) { return records.stream().filter(x -> id.equals(x.id())).findFirst(); }
        public List<VerificationRecord> findBySimulationId(UUID id) { return records.stream().filter(x -> id.equals(x.simulationId())).toList(); }
    }

    private static final class FakeWorkflow implements VerificationWorkflowClient {
        private int verifyCalls;
        private int syncCalls;
        private boolean syncSuccessful = true;
        public Optional<RemediationResponse> findRemediation(UUID id) { return Optional.empty(); }
        public List<RemediationResponse> findRemediations(UUID id) { return List.of(); }
        public VerificationCheckResult verifyPatch(RemediationResponse remediation) {
            verifyCalls++; return new VerificationCheckResult("PASSED", "Patch is applied");
        }
        public boolean synchronizeOutcome(RemediationResponse remediation, VerificationCheckResult result) {
            syncCalls++; return syncSuccessful;
        }
        public void completeVerificationStage(UUID simulationId, UUID roundId) { }
    }
}
