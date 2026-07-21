package com.cybersim.scoringservice.controller;

import com.cybersim.scoringservice.model.ScoreEventRecord;
import com.cybersim.scoringservice.store.ScoreAppendResult;
import com.cybersim.scoringservice.store.ScoreEventStore;
import com.cybersim.shared.dto.ApiErrorResponse;
import com.cybersim.shared.dto.ScoreEventCreateRequest;
import com.cybersim.shared.dto.ScoreEventResponse;
import com.cybersim.shared.dto.SimulationScoreResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringControllerTest {
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private InMemoryStore store;
    private ScoringController controller;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        controller = new ScoringController(store);
    }

    @Test
    void enforcesAllSrsPointRules() {
        Map<String, Integer> expectedRules = new LinkedHashMap<>();
        expectedRules.put("RED_LOW_FINDING", 10);
        expectedRules.put("RED_MEDIUM_FINDING", 25);
        expectedRules.put("RED_HIGH_FINDING", 50);
        expectedRules.put("RED_CRITICAL_FINDING", 100);
        expectedRules.put("RED_DUPLICATE_FINDING", -5);
        expectedRules.put("RED_UNSAFE_ACTION", -100);
        expectedRules.put("BLUE_VALID_DETECTION", 20);
        expectedRules.put("BLUE_CORRECT_TRIAGE", 20);
        expectedRules.put("BLUE_VALID_REMEDIATION_PROPOSAL", 25);
        expectedRules.put("BLUE_PATCH_APPLIED", 40);
        expectedRules.put("BLUE_FIX_VERIFIED", 30);
        expectedRules.put("BLUE_FALSE_POSITIVE", -10);
        expectedRules.put("BLUE_FAILED_PATCH", -15);

        expectedRules.forEach((scoreType, points) -> {
            ScoreEventCreateRequest request = request(UUID.randomUUID(), scoreType,
                    "RED_UNSAFE_ACTION".equals(scoreType) ? UUID.randomUUID() : null);
            ResponseEntity<Object> response = controller.append(request);
            ScoreEventResponse event = (ScoreEventResponse) response.getBody();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(event.points()).isEqualTo(points);
            assertThat(event.team()).isEqualTo(scoreType.startsWith("RED_") ? "RED" : "BLUE");
        });
    }

    @Test
    void retryIsIdempotentAndConflictingReuseIsRejected() {
        UUID sourceEventId = UUID.randomUUID();
        ScoreEventCreateRequest original = request(sourceEventId, "BLUE_VALID_DETECTION", null);

        ResponseEntity<Object> created = controller.append(original);
        ResponseEntity<Object> retry = controller.append(original);
        ResponseEntity<Object> conflict = controller.append(
                request(sourceEventId, "BLUE_PATCH_APPLIED", null));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((ScoreEventResponse) retry.getBody()).id())
                .isEqualTo(((ScoreEventResponse) created.getBody()).id());
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(controller.scoreEvents(SIMULATION_ID)).hasSize(1);
    }

    @Test
    void calculatesTotalsRiskAndBlockedAgentsFromEvents() {
        UUID blockedAgentId = UUID.randomUUID();
        controller.append(request(UUID.randomUUID(), "RED_CRITICAL_FINDING", null));
        controller.append(request(UUID.randomUUID(), "RED_UNSAFE_ACTION", blockedAgentId));
        controller.append(request(UUID.randomUUID(), "BLUE_PATCH_APPLIED", null));
        controller.append(request(UUID.randomUUID(), "BLUE_FIX_VERIFIED", null));

        SimulationScoreResponse scores = controller.scores(SIMULATION_ID);

        assertThat(scores.redTeamScore()).isZero();
        assertThat(scores.blueTeamScore()).isEqualTo(70);
        assertThat(scores.finalRiskScore()).isZero();
        assertThat(scores.eventCount()).isEqualTo(4);
        assertThat(scores.blockedAgentIds()).containsExactly(blockedAgentId);
    }

    @Test
    void rejectsUnsafePenaltyWithoutAgent() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ScoreEventCreateRequest unsafeWithoutAgent = request(UUID.randomUUID(), "RED_UNSAFE_ACTION", null);

        assertThat(validator.validate(unsafeWithoutAgent)).isNotEmpty();
    }

    private ScoreEventCreateRequest request(UUID sourceEventId, String scoreType, UUID agentId) {
        return new ScoreEventCreateRequest(SIMULATION_ID, null, sourceEventId, scoreType, agentId);
    }

    private static final class InMemoryStore implements ScoreEventStore {
        private final Map<String, ScoreEventRecord> events = new ConcurrentHashMap<>();

        @Override
        public ScoreAppendResult append(ScoreEventRecord event) {
            String key = event.simulationId() + ":" + event.sourceEventId();
            ScoreEventRecord existing = events.putIfAbsent(key, event);
            return existing == null ? new ScoreAppendResult(event, true) : new ScoreAppendResult(existing, false);
        }

        @Override
        public Optional<ScoreEventRecord> findBySimulationIdAndSourceEventId(UUID simulationId, UUID sourceEventId) {
            return Optional.ofNullable(events.get(simulationId + ":" + sourceEventId));
        }

        @Override
        public List<ScoreEventRecord> findBySimulationId(UUID simulationId) {
            return events.values().stream().filter(event -> simulationId.equals(event.simulationId()))
                    .sorted(Comparator.comparing(ScoreEventRecord::createdAt).thenComparing(ScoreEventRecord::id))
                    .toList();
        }
    }
}
