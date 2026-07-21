package com.cybersim.remediationservice.controller;

import com.cybersim.remediationservice.model.RemediationRecord;
import com.cybersim.remediationservice.store.RemediationStore;
import com.cybersim.shared.dto.ApiErrorResponse;
import com.cybersim.shared.dto.RemediationCreateRequest;
import com.cybersim.shared.dto.RemediationResponse;
import com.cybersim.shared.dto.VerificationOutcomeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class RemediationVerificationControllerTest {
    private InMemoryStore store;
    private RemediationVerificationController controller;
    private UUID remediationId;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        RemediationRecord applied = RemediationRecord.from(new RemediationCreateRequest(
                UUID.randomUUID(), null, UUID.randomUUID(), null, UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000101"), "AUTH_REQUIRED", "Require auth"))
                .approve().applied(true, "Applied");
        remediationId = store.save(applied).id();
        controller = new RemediationVerificationController(store);
    }

    @Test
    void passedOutcomeMarksAppliedRemediationVerified() {
        ResponseEntity<Object> response = controller.recordVerification(remediationId,
                new VerificationOutcomeRequest("PASSED", "Patch observed"));

        RemediationResponse body = (RemediationResponse) response.getBody();
        assertThat(body.status()).isEqualTo("VERIFIED");
        assertThat(body.verifiedAt()).isNotNull();
    }

    private static final class InMemoryStore implements RemediationStore {
        private final Map<UUID, RemediationRecord> records = new ConcurrentHashMap<>();

        public RemediationRecord save(RemediationRecord remediation) {
            records.put(remediation.id(), remediation);
            return remediation;
        }

        public Optional<RemediationRecord> findById(UUID id) {
            return Optional.ofNullable(records.get(id));
        }

        public List<RemediationRecord> findBySimulationId(UUID simulationId) {
            return records.values().stream().filter(record -> simulationId.equals(record.simulationId())).toList();
        }
    }
}
