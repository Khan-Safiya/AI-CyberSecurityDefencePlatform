package com.cybersim.verificationservice.persistence;

import com.cybersim.shared.dto.RemediationResponse;
import com.cybersim.verificationservice.model.VerificationRecord;
import com.cybersim.verificationservice.store.VerificationStore;
import com.cybersim.verificationservice.workflow.VerificationCheckResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VerificationPersistenceIntegrationTest {
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Autowired
    private VerificationStore store;
    @Autowired
    private ConsumedMessageStore inbox;

    @Test
    void flywayCreatesEmptyResultStore() {
        assertThat(store.findBySimulationId(SIMULATION_ID)).isEmpty();
    }

    @Test
    void persistsResultRelationshipsEvidenceAndTimestamp() {
        Instant now = Instant.now();
        RemediationResponse remediation = new RemediationResponse(UUID.randomUUID(), SIMULATION_ID, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000101"), "INPUT_VALIDATION",
                "Validate input", "APPLIED", "Applied", now, now, now, now, null, null);
        VerificationRecord created = store.save(VerificationRecord.from(remediation,
                new VerificationCheckResult("PASSED", "Expected safe patch state observed")));

        VerificationRecord reloaded = store.findById(created.id()).orElseThrow();

        assertThat(reloaded.status()).isEqualTo("PASSED");
        assertThat(reloaded.remediationId()).isEqualTo(remediation.id());
        assertThat(reloaded.vulnerabilityId()).isEqualTo(remediation.vulnerabilityId());
        assertThat(reloaded.evidenceSummary()).contains("patch state");
        assertThat(reloaded.verifiedAt()).isNotNull();
    }

    @Test
    void persistsConsumedMessageId() {
        UUID messageId = UUID.fromString("00000000-0000-0000-0000-000000001001");
        inbox.record(messageId);
        assertThat(inbox.contains(messageId)).isTrue();
    }
}
