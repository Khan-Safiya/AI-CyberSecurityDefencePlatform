package com.cybersim.detectionengineservice.controller;

import com.cybersim.detectionengineservice.model.DetectionEventRecord;
import com.cybersim.detectionengineservice.model.DetectionRuleRecord;
import com.cybersim.detectionengineservice.store.DetectionEventStore;
import com.cybersim.detectionengineservice.store.DetectionRuleStore;
import com.cybersim.shared.dto.ApiErrorResponse;
import com.cybersim.shared.dto.DetectionEventCreateRequest;
import com.cybersim.shared.dto.DetectionEventResponse;
import com.cybersim.shared.dto.DetectionRuleCreateRequest;
import com.cybersim.shared.dto.DetectionRuleResponse;
import com.cybersim.shared.dto.DetectionRuleUpdateRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DetectionControllerTest {
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID VULNERABILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");

    private InMemoryRuleStore ruleStore;
    private InMemoryEventStore eventStore;
    private DetectionController controller;

    @BeforeEach
    void setUp() {
        ruleStore = new InMemoryRuleStore();
        eventStore = new InMemoryEventStore();
        controller = new DetectionController(ruleStore, eventStore);
    }

    @Test
    void createsUpdatesAndDeletesRule() {
        ResponseEntity<DetectionRuleResponse> created = controller.createRule(new DetectionRuleCreateRequest(
                "Safe login observation", "Observes authorized login checks", "action.type=AUTH_TEST",
                "MEDIUM", true));

        UUID id = created.getBody().id();
        ResponseEntity<Object> updated = controller.updateRule(id,
                new DetectionRuleUpdateRequest(null, null, null, "HIGH", false));
        ResponseEntity<Object> deleted = controller.deleteRule(id);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((DetectionRuleResponse) updated.getBody()).satisfies(rule -> {
            assertThat(rule.severity()).isEqualTo("HIGH");
            assertThat(rule.enabled()).isFalse();
        });
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(controller.rules()).isEmpty();
    }

    @Test
    void recordsDetectionLinkedToVulnerability() {
        ResponseEntity<DetectionEventResponse> created = controller.createDetection(new DetectionEventCreateRequest(
                SIMULATION_ID, null, TARGET_ID, "RED_TEAM_ACTION", "detection.created", "MEDIUM",
                "Authorized login testing observed", null, VULNERABILITY_ID,
                Map.of("ruleId", "00000000-0000-0000-0000-000000000601")));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.detections(SIMULATION_ID)).singleElement().satisfies(event -> {
            assertThat(event.relatedVulnerabilityId()).isEqualTo(VULNERABILITY_ID);
            assertThat(event.metadata()).containsEntry("ruleId", "00000000-0000-0000-0000-000000000601");
        });
    }

    @Test
    void identicalDetectionRetryReturnsOriginalEvent() {
        UUID key = UUID.fromString("00000000-0000-0000-0000-000000000799");
        DetectionEventCreateRequest request = detectionRequest("Authorized check observed");

        ResponseEntity<DetectionEventResponse> first = controller.createDetection(request, key);
        ResponseEntity<DetectionEventResponse> retry = controller.createDetection(request, key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody().id()).isEqualTo(key);
        assertThat(eventStore.events).hasSize(1);
    }

    @Test
    void detectionKeyCannotBeReusedForDifferentData() {
        UUID key = UUID.fromString("00000000-0000-0000-0000-000000000798");
        controller.createDetection(detectionRequest("Original"), key);

        assertThatThrownBy(() -> controller.createDetection(detectionRequest("Different"), key))
                .isInstanceOf(com.cybersim.shared.exceptions.ConflictException.class);
    }

    @Test
    void missingRuleReturnsStandardError() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000009991");

        ResponseEntity<Object> response = controller.rule(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        assertThat(((ApiErrorResponse) response.getBody()).path()).isEqualTo("/detection-rules/" + id);
    }

    private DetectionEventCreateRequest detectionRequest(String message) {
        return new DetectionEventCreateRequest(SIMULATION_ID, null, TARGET_ID, "RED_TEAM_FINDING",
                "detection.created", "MEDIUM", message, null, VULNERABILITY_ID,
                Map.of("ruleId", "00000000-0000-0000-0000-000000000601"));
    }

    private static final class InMemoryRuleStore implements DetectionRuleStore {
        private final Map<UUID, DetectionRuleRecord> rules = new ConcurrentHashMap<>();

        @Override
        public DetectionRuleRecord save(DetectionRuleRecord rule) {
            rules.put(rule.id(), rule);
            return rule;
        }

        @Override
        public Optional<DetectionRuleRecord> findById(UUID id) {
            return Optional.ofNullable(rules.get(id));
        }

        @Override
        public List<DetectionRuleRecord> findAll() {
            return rules.values().stream().sorted(Comparator.comparing(DetectionRuleRecord::id)).toList();
        }

        @Override
        public void deleteById(UUID id) {
            rules.remove(id);
        }
    }

    private static final class InMemoryEventStore implements DetectionEventStore {
        private final Map<UUID, DetectionEventRecord> events = new ConcurrentHashMap<>();

        @Override
        public DetectionEventRecord save(DetectionEventRecord event) {
            events.put(event.id(), event);
            return event;
        }

        @Override
        public Optional<DetectionEventRecord> findById(UUID id) {
            return Optional.ofNullable(events.get(id));
        }

        @Override
        public List<DetectionEventRecord> findBySimulationId(UUID simulationId) {
            return events.values().stream()
                    .filter(event -> simulationId.equals(event.simulationId()))
                    .sorted(Comparator.comparing(DetectionEventRecord::createdAt).thenComparing(DetectionEventRecord::id))
                    .toList();
        }
    }
}
