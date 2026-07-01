package com.cybersim.remediationservice.persistence;

import com.cybersim.remediationservice.model.RemediationRecord;
import com.cybersim.remediationservice.store.RemediationStore;
import com.cybersim.shared.dto.RemediationCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RemediationPersistenceIntegrationTest {
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Autowired
    private RemediationStore store;
    @Autowired
    private ConsumedMessageStore inbox;

    @Test
    void flywaySeedsSixStableProposalsLinkedToBaselineFindings() {
        assertThat(store.findBySimulationId(SIMULATION_ID))
                .hasSize(6)
                .allSatisfy(remediation -> assertThat(remediation.status()).isEqualTo("PROPOSED"))
                .extracting(RemediationRecord::id)
                .contains(UUID.fromString("00000000-0000-0000-0000-000000000801"));
    }

    @Test
    void persistsApprovalApplicationAndRollbackTimestamps() {
        RemediationRecord created = store.save(RemediationRecord.from(new RemediationCreateRequest(
                SIMULATION_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), TARGET_ID,
                "INPUT_VALIDATION", "Validate harmless billing search input")));

        store.save(created.approve());
        RemediationRecord applied = store.save(store.findById(created.id()).orElseThrow()
                .applied(true, "Patch applied to sandbox target"));
        store.save(applied.rolledBack(true, "Patch rolled back on sandbox target"));

        RemediationRecord reloaded = store.findById(created.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo("ROLLED_BACK");
        assertThat(reloaded.approvedAt()).isNotNull();
        assertThat(reloaded.appliedAt()).isNotNull();
        assertThat(reloaded.rolledBackAt()).isNotNull();
        assertThat(reloaded.outcomeSummary()).contains("rolled back");
    }

    @Test
    void persistsConsumedMessageId() {
        UUID messageId = UUID.fromString("00000000-0000-0000-0000-000000000901");
        inbox.record(messageId);
        assertThat(inbox.contains(messageId)).isTrue();
    }
}
