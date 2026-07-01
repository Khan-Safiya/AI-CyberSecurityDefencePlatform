package com.cybersim.detectionengineservice.persistence;

import com.cybersim.detectionengineservice.model.DetectionEventRecord;
import com.cybersim.detectionengineservice.model.DetectionRuleRecord;
import com.cybersim.detectionengineservice.store.DetectionEventStore;
import com.cybersim.detectionengineservice.store.DetectionRuleStore;
import com.cybersim.shared.dto.DetectionEventCreateRequest;
import com.cybersim.shared.dto.DetectionRuleCreateRequest;
import com.cybersim.shared.dto.DetectionRuleUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DetectionPersistenceIntegrationTest {
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID VULNERABILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");

    @Autowired
    private DetectionRuleStore ruleStore;
    @Autowired
    private DetectionEventStore eventStore;
    @Autowired
    private ConsumedMessageStore inbox;

    @Test
    void flywaySeedsStableSafeRulesAndDetections() {
        assertThat(ruleStore.findAll())
                .hasSize(6)
                .extracting(DetectionRuleRecord::id)
                .contains(UUID.fromString("00000000-0000-0000-0000-000000000601"));
        assertThat(eventStore.findBySimulationId(SIMULATION_ID))
                .hasSize(3)
                .extracting(DetectionEventRecord::relatedVulnerabilityId)
                .contains(VULNERABILITY_ID);
    }

    @Test
    void persistsRuleUpdatesAndDeletes() {
        DetectionRuleRecord created = ruleStore.save(DetectionRuleRecord.from(new DetectionRuleCreateRequest(
                "Persistent rule", "Safe description", "action.type=SAFE_TEST", "LOW", true)));
        ruleStore.save(created.update(new DetectionRuleUpdateRequest(null, null, null, "HIGH", false)));

        DetectionRuleRecord reloaded = ruleStore.findById(created.id()).orElseThrow();
        assertThat(reloaded.severity()).isEqualTo("HIGH");
        assertThat(reloaded.enabled()).isFalse();

        ruleStore.deleteById(created.id());
        assertThat(ruleStore.findById(created.id())).isEmpty();
    }

    @Test
    void persistsDetectionMetadataAndRelationships() {
        DetectionEventRecord created = eventStore.save(DetectionEventRecord.from(new DetectionEventCreateRequest(
                SIMULATION_ID, UUID.randomUUID(), TARGET_ID, "POLICY_ENGINE", "detection.created", "CRITICAL",
                "Unsafe action was blocked", UUID.randomUUID(), VULNERABILITY_ID,
                Map.of("decision", "DENY", "reason", "outside allowed scope"))));

        DetectionEventRecord reloaded = eventStore.findById(created.id()).orElseThrow();
        assertThat(reloaded.relatedVulnerabilityId()).isEqualTo(VULNERABILITY_ID);
        assertThat(reloaded.metadata()).containsEntry("decision", "DENY");
        assertThat(eventStore.findBySimulationId(SIMULATION_ID))
                .extracting(DetectionEventRecord::id)
                .contains(created.id());
    }

    @Test
    void persistsConsumedMessageId() {
        UUID messageId = UUID.fromString("00000000-0000-0000-0000-000000000801");
        inbox.record(messageId);
        assertThat(inbox.contains(messageId)).isTrue();
    }
}
