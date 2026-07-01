package com.cybersim.remediationservice.controller;

import com.cybersim.remediationservice.model.RemediationRecord;
import com.cybersim.remediationservice.model.RemediationType;
import com.cybersim.remediationservice.patch.PatchExecutionResult;
import com.cybersim.remediationservice.patch.SandboxPatchClient;
import com.cybersim.remediationservice.store.RemediationStore;
import com.cybersim.shared.dto.ApiErrorResponse;
import com.cybersim.shared.dto.RemediationCreateRequest;
import com.cybersim.shared.dto.RemediationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemediationControllerTest {
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private InMemoryStore store;
    private FakePatchClient patchClient;
    private RemediationController controller;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        patchClient = new FakePatchClient();
        controller = new RemediationController(store, patchClient);
    }

    @Test
    void createsProposedRemediationLinkedToDetectionAndFinding() {
        ResponseEntity<RemediationResponse> response = controller.create(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).satisfies(remediation -> {
            assertThat(remediation.status()).isEqualTo("PROPOSED");
            assertThat(remediation.vulnerabilityId()).isEqualTo(request().vulnerabilityId());
            assertThat(remediation.detectionId()).isEqualTo(request().detectionId());
        });
        assertThat(controller.list(SIMULATION_ID)).hasSize(1);
    }

    @Test
    void identicalIdempotentRetryReturnsOriginalProposal() {
        UUID key = UUID.fromString("00000000-0000-0000-0000-000000000899");
        ResponseEntity<RemediationResponse> first = controller.create(request(), key);
        ResponseEntity<RemediationResponse> retry = controller.create(request(), key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody().id()).isEqualTo(key);
        assertThat(store.actions).hasSize(1);
    }

    @Test
    void remediationKeyCannotBeReusedForDifferentProposal() {
        UUID key = UUID.fromString("00000000-0000-0000-0000-000000000898");
        controller.create(request(), key);
        RemediationCreateRequest different = new RemediationCreateRequest(SIMULATION_ID, null,
                request().vulnerabilityId(), request().detectionId(), request().agentId(), TARGET_ID,
                "AUTH_REQUIRED", "Different patch summary");

        assertThatThrownBy(() -> controller.create(different, key))
                .isInstanceOf(com.cybersim.shared.exceptions.ConflictException.class);
    }

    @Test
    void requiresApprovalBeforeApplyingThenSupportsRollback() {
        UUID id = controller.create(request()).getBody().id();

        ResponseEntity<Object> prematureApply = controller.apply(id);
        ResponseEntity<Object> approved = controller.approve(id);
        ResponseEntity<Object> applied = controller.apply(id);
        ResponseEntity<Object> rolledBack = controller.rollback(id);

        assertThat(prematureApply.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((RemediationResponse) approved.getBody()).approvedAt()).isNotNull();
        assertThat(((RemediationResponse) applied.getBody())).satisfies(remediation -> {
            assertThat(remediation.status()).isEqualTo("APPLIED");
            assertThat(remediation.appliedAt()).isNotNull();
        });
        assertThat(((RemediationResponse) rolledBack.getBody())).satisfies(remediation -> {
            assertThat(remediation.status()).isEqualTo("ROLLED_BACK");
            assertThat(remediation.rolledBackAt()).isNotNull();
        });
        assertThat(patchClient.appliedType).isEqualTo(RemediationType.AUTH_REQUIRED);
        assertThat(patchClient.rolledBackType).isEqualTo(RemediationType.AUTH_REQUIRED);
    }

    @Test
    void failedApplicationIsStoredAndCanBeRetried() {
        UUID id = controller.create(request()).getBody().id();
        controller.approve(id);
        patchClient.failApply = true;

        RemediationResponse failed = (RemediationResponse) controller.apply(id).getBody();
        patchClient.failApply = false;
        RemediationResponse retried = (RemediationResponse) controller.apply(id).getBody();

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.outcomeSummary()).contains("unavailable");
        assertThat(retried.status()).isEqualTo("APPLIED");
    }

    @Test
    void automatedApplicationRejectsNonSandboxTarget() {
        RemediationCreateRequest externalTargetRequest = new RemediationCreateRequest(
                SIMULATION_ID, null, UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
                "AUTH_REQUIRED", "External targets require a separate approved integration");
        UUID id = controller.create(externalTargetRequest).getBody().id();
        controller.approve(id);

        ResponseEntity<Object> response = controller.apply(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((ApiErrorResponse) response.getBody()).message()).contains("built-in sandbox");
        assertThat(patchClient.appliedType).isNull();
    }

    @Test
    void missingRemediationReturnsStandardError() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000009991");

        ResponseEntity<Object> response = controller.get(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        assertThat(((ApiErrorResponse) response.getBody()).path()).isEqualTo("/remediations/" + id);
    }

    private RemediationCreateRequest request() {
        return new RemediationCreateRequest(SIMULATION_ID, null,
                UUID.fromString("00000000-0000-0000-0000-000000000501"),
                UUID.fromString("00000000-0000-0000-0000-000000000701"),
                UUID.fromString("00000000-0000-0000-0000-000000000302"), TARGET_ID,
                "AUTH_REQUIRED", "Require authentication for the admin report");
    }

    private static final class InMemoryStore implements RemediationStore {
        private final Map<UUID, RemediationRecord> actions = new ConcurrentHashMap<>();

        @Override
        public RemediationRecord save(RemediationRecord remediation) {
            actions.put(remediation.id(), remediation);
            return remediation;
        }

        @Override
        public Optional<RemediationRecord> findById(UUID id) {
            return Optional.ofNullable(actions.get(id));
        }

        @Override
        public List<RemediationRecord> findBySimulationId(UUID simulationId) {
            return actions.values().stream().filter(action -> simulationId.equals(action.simulationId()))
                    .sorted(Comparator.comparing(RemediationRecord::createdAt).thenComparing(RemediationRecord::id))
                    .toList();
        }
    }

    private static final class FakePatchClient implements SandboxPatchClient {
        private boolean failApply;
        private RemediationType appliedType;
        private RemediationType rolledBackType;

        @Override
        public PatchExecutionResult apply(RemediationType remediationType) {
            appliedType = remediationType;
            return failApply ? PatchExecutionResult.failure("Target patch service is unavailable")
                    : PatchExecutionResult.success("Patch applied to sandbox target");
        }

        @Override
        public PatchExecutionResult rollback(RemediationType remediationType) {
            rolledBackType = remediationType;
            return PatchExecutionResult.success("Patch rolled back on sandbox target");
        }
    }
}
