package com.cybersim.detectionengineservice.workflow;

import com.cybersim.detectionengineservice.model.DetectionEventRecord;
import com.cybersim.detectionengineservice.store.DetectionEventStore;
import com.cybersim.shared.dto.VulnerabilityResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionStageProcessorTest {
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Test
    void persistsOnlyCurrentRoundFindingsAndIsIdempotent() {
        InMemoryEventStore store = new InMemoryEventStore();
        DetectionStageProcessor processor = new DetectionStageProcessor(store);
        List<VulnerabilityResponse> findings = List.of(
                finding(UUID.fromString("00000000-0000-0000-0000-000000000901"), ROUND_ID, "AUTHENTICATION"),
                finding(UUID.fromString("00000000-0000-0000-0000-000000000902"), UUID.randomUUID(), "RATE_LIMIT"));

        int first = processor.persist(MESSAGE_ID, SIMULATION_ID, ROUND_ID, findings);
        int retry = processor.persist(MESSAGE_ID, SIMULATION_ID, ROUND_ID, findings);

        assertThat(first).isEqualTo(1);
        assertThat(retry).isZero();
        assertThat(store.events).singleElement().satisfies(event -> {
            assertThat(event.relatedVulnerabilityId()).isEqualTo(findings.getFirst().id());
            assertThat(event.metadata()).containsKey("ruleId");
        });
    }

    private VulnerabilityResponse finding(UUID id, UUID roundId, String type) {
        return new VulnerabilityResponse(id, SIMULATION_ID, roundId, TARGET_ID, "Finding", "Description", type,
                "HIGH", "OPEN", "Evidence", "/demo/test", "Safe steps", "Fix", null, null,
                Instant.now(), Instant.now(), null);
    }

    private static final class InMemoryEventStore implements DetectionEventStore {
        private final List<DetectionEventRecord> events = new ArrayList<>();
        public DetectionEventRecord save(DetectionEventRecord event) { events.add(event); return event; }
        public Optional<DetectionEventRecord> findById(UUID id) {
            return events.stream().filter(event -> id.equals(event.id())).findFirst();
        }
        public List<DetectionEventRecord> findBySimulationId(UUID simulationId) {
            return events.stream().filter(event -> simulationId.equals(event.simulationId())).toList();
        }
    }
}
