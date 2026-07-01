package com.cybersim.verificationservice.controller;

import com.cybersim.shared.dto.ApiErrorResponse;
import com.cybersim.shared.dto.RemediationResponse;
import com.cybersim.shared.dto.VerificationCreateRequest;
import com.cybersim.shared.dto.VerificationResponse;
import com.cybersim.verificationservice.model.VerificationRecord;
import com.cybersim.verificationservice.store.VerificationStore;
import com.cybersim.verificationservice.workflow.VerificationCheckResult;
import com.cybersim.verificationservice.workflow.VerificationWorkflowClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationControllerTest {
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID REMEDIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private InMemoryStore store;
    private FakeWorkflowClient workflow;
    private VerificationController controller;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        workflow = new FakeWorkflowClient();
        controller = new VerificationController(store, workflow);
    }

    @Test
    void verifiesAppliedPatchAndStoresPassedEvidence() {
        workflow.remediation = remediation("APPLIED", Instant.now());

        ResponseEntity<Object> response = controller.verify(new VerificationCreateRequest(REMEDIATION_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((VerificationResponse) response.getBody()).satisfies(result -> {
            assertThat(result.status()).isEqualTo("PASSED");
            assertThat(result.evidenceSummary()).contains("expected patch");
            assertThat(result.remediationId()).isEqualTo(REMEDIATION_ID);
        });
        assertThat(workflow.synchronizationCalled).isTrue();
        assertThat(controller.list(SIMULATION_ID)).hasSize(1);
    }

    @Test
    void identicalIdempotentRetryReturnsOriginalVerification() {
        workflow.remediation = remediation("APPLIED", Instant.now());
        UUID key = UUID.fromString("00000000-0000-0000-0000-000000000999");

        ResponseEntity<Object> first = controller.verify(new VerificationCreateRequest(REMEDIATION_ID), key);
        ResponseEntity<Object> retry = controller.verify(new VerificationCreateRequest(REMEDIATION_ID), key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((VerificationResponse) retry.getBody()).id()).isEqualTo(key);
        assertThat(store.results).hasSize(1);
    }

    @Test
    void rejectsVerificationBeforePatchIsApplied() {
        workflow.remediation = remediation("APPROVED", null);

        ResponseEntity<Object> response = controller.verify(new VerificationCreateRequest(REMEDIATION_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(store.findBySimulationId(SIMULATION_ID)).isEmpty();
    }

    @Test
    void recordsPendingSynchronizationWithoutChangingCheckResult() {
        workflow.remediation = remediation("APPLIED", Instant.now());
        workflow.synchronizationSucceeds = false;
        workflow.result = new VerificationCheckResult("INCONCLUSIVE", "Sandbox status unavailable.");

        VerificationResponse response = (VerificationResponse) controller
                .verify(new VerificationCreateRequest(REMEDIATION_ID)).getBody();

        assertThat(response.status()).isEqualTo("INCONCLUSIVE");
        assertThat(response.evidenceSummary()).contains("synchronization is pending");
    }

    @Test
    void missingRemediationReturnsStandardError() {
        workflow.remediation = null;

        ResponseEntity<Object> response = controller.verify(new VerificationCreateRequest(REMEDIATION_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
    }

    private RemediationResponse remediation(String status, Instant appliedAt) {
        Instant now = Instant.now();
        return new RemediationResponse(REMEDIATION_ID, SIMULATION_ID, null,
                UUID.fromString("00000000-0000-0000-0000-000000000501"),
                UUID.fromString("00000000-0000-0000-0000-000000000701"),
                UUID.fromString("00000000-0000-0000-0000-000000000302"),
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "AUTH_REQUIRED", "Require authentication", status, null, now, now, now, appliedAt, null, null);
    }

    private static final class InMemoryStore implements VerificationStore {
        private final Map<UUID, VerificationRecord> results = new ConcurrentHashMap<>();

        @Override
        public VerificationRecord save(VerificationRecord verification) {
            results.put(verification.id(), verification);
            return verification;
        }

        @Override
        public Optional<VerificationRecord> findById(UUID id) {
            return Optional.ofNullable(results.get(id));
        }

        @Override
        public List<VerificationRecord> findBySimulationId(UUID simulationId) {
            return results.values().stream().filter(result -> simulationId.equals(result.simulationId()))
                    .sorted(Comparator.comparing(VerificationRecord::verifiedAt).thenComparing(VerificationRecord::id))
                    .toList();
        }
    }

    private static final class FakeWorkflowClient implements VerificationWorkflowClient {
        private RemediationResponse remediation;
        private VerificationCheckResult result = new VerificationCheckResult(
                "PASSED", "The sandbox reports the expected patch as applied.");
        private boolean synchronizationSucceeds = true;
        private boolean synchronizationCalled;

        @Override
        public Optional<RemediationResponse> findRemediation(UUID remediationId) {
            return Optional.ofNullable(remediation);
        }

        @Override
        public List<RemediationResponse> findRemediations(UUID simulationId) {
            return remediation == null ? List.of() : List.of(remediation);
        }

        @Override
        public VerificationCheckResult verifyPatch(RemediationResponse remediation) {
            return result;
        }

        @Override
        public boolean synchronizeOutcome(RemediationResponse remediation, VerificationCheckResult result) {
            synchronizationCalled = true;
            return synchronizationSucceeds;
        }

        @Override
        public void completeVerificationStage(UUID simulationId, UUID roundId) { }
    }
}
