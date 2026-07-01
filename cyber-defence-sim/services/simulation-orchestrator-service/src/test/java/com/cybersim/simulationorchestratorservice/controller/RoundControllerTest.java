package com.cybersim.simulationorchestratorservice.controller;

import com.cybersim.shared.dto.RoundCompletionRequest;
import com.cybersim.shared.dto.RoundCompletionResponse;
import com.cybersim.shared.dto.SimulationResponse;
import com.cybersim.shared.dto.SimulationRoundResponse;
import com.cybersim.shared.dto.TargetMode;
import com.cybersim.simulationorchestratorservice.model.SimulationRecord;
import com.cybersim.simulationorchestratorservice.model.SimulationRoundRecord;
import com.cybersim.simulationorchestratorservice.outbox.OutboxEventFactory;
import com.cybersim.simulationorchestratorservice.outbox.OutboxEventRecord;
import com.cybersim.simulationorchestratorservice.outbox.OutboxStore;
import com.cybersim.simulationorchestratorservice.store.SimulationRoundStore;
import com.cybersim.simulationorchestratorservice.store.SimulationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;

class RoundControllerTest {
    private InMemorySimulationStore simulationStore;
    private InMemoryRoundStore roundStore;
    private RoundController controller;
    private InMemoryOutboxStore outboxStore;

    @BeforeEach
    void setUp() {
        simulationStore = new InMemorySimulationStore();
        roundStore = new InMemoryRoundStore();
        outboxStore = new InMemoryOutboxStore();
        controller = new RoundController(simulationStore, roundStore, outboxStore,
                new OutboxEventFactory(new ObjectMapper()), "service-token");
    }

    @Test
    void advancesInOrderAndCreatesNextRoundWhenNoStopConditionMatches() {
        SimulationRecord simulation = createSimulation(3, 2, Instant.now());
        SimulationRoundRecord round = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, Instant.now()));

        assertThat(advance(simulation.id(), round.id()).status()).isEqualTo("RED_TEAM_RUNNING");
        assertThat(advance(simulation.id(), round.id()).status()).isEqualTo("DETECTION_RUNNING");
        assertThat(advance(simulation.id(), round.id()).status()).isEqualTo("BLUE_TEAM_RUNNING");
        assertThat(advance(simulation.id(), round.id()).status()).isEqualTo("VERIFYING");
        assertThat(advance(simulation.id(), round.id()).status()).isEqualTo("SCORING");

        RoundCompletionResponse response = complete(simulation.id(), round.id(), outcome(2, false, false, true, false));

        assertThat(response.simulation().status()).isEqualTo("RUNNING");
        assertThat(response.simulation().currentRound()).isEqualTo(2);
        assertThat(response.completedRound().status()).isEqualTo("COMPLETED");
        assertThat(response.nextRound().roundNumber()).isEqualTo(2);
        assertThat(roundStore.findBySimulationId(simulation.id())).hasSize(2);
        assertThat(outboxStore.findBySimulationId(simulation.id()))
                .extracting(OutboxEventRecord::eventType)
                .contains("simulation.round.red-team.requested", "simulation.round.detection.requested",
                        "simulation.round.blue-team.requested",
                        "simulation.round.verification.requested", "simulation.round.completed",
                        "simulation.round.created");
    }

    @Test
    void stopsAfterConfiguredConsecutiveRoundsWithoutFindings() {
        SimulationRecord simulation = createSimulation(5, 2, Instant.now());
        SimulationRoundRecord first = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, Instant.now()));
        advanceToScoring(simulation.id(), first.id());
        RoundCompletionResponse firstResult = complete(simulation.id(), first.id(), outcome(0, false, false, true, false));
        advanceToScoring(simulation.id(), firstResult.nextRound().id());

        RoundCompletionResponse secondResult = complete(simulation.id(), firstResult.nextRound().id(),
                outcome(0, false, false, true, false));

        assertThat(secondResult.simulation().status()).isEqualTo("COMPLETED");
        assertThat(secondResult.stopReason()).isEqualTo("NO_NEW_FINDINGS_THRESHOLD_REACHED");
        assertThat(secondResult.nextRound()).isNull();
    }

    @Test
    void enforcesMaximumRoundsAndDuration() {
        SimulationRecord maxRoundsSimulation = createSimulation(1, 1, Instant.now());
        SimulationRoundRecord maxRound = roundStore.save(SimulationRoundRecord.create(maxRoundsSimulation.id(), 1, Instant.now()));
        advanceToScoring(maxRoundsSimulation.id(), maxRound.id());
        RoundCompletionResponse maxRoundsResult = complete(maxRoundsSimulation.id(), maxRound.id(),
                outcome(1, false, false, true, false));

        SimulationRecord expiredSimulation = createSimulation(5, 2, Instant.now().minus(61, ChronoUnit.MINUTES));
        SimulationRoundRecord expiredRound = roundStore.save(SimulationRoundRecord.create(expiredSimulation.id(), 1, Instant.now()));
        advanceToScoring(expiredSimulation.id(), expiredRound.id());
        RoundCompletionResponse durationResult = complete(expiredSimulation.id(), expiredRound.id(),
                outcome(1, false, false, true, false));

        assertThat(maxRoundsResult.stopReason()).isEqualTo("MAX_ROUNDS_REACHED");
        assertThat(durationResult.stopReason()).isEqualTo("MAX_DURATION_REACHED");
    }

    @Test
    void handlesSuccessfulAndSafetyTerminalConditions() {
        assertTerminal(outcome(1, true, false, true, false), "COMPLETED", "ALL_HIGH_CRITICAL_FIXED");
        assertTerminal(outcome(1, false, true, true, false), "FAILED", "UNSAFE_ACTION_DETECTED");
        assertTerminal(outcome(1, false, false, false, false), "FAILED", "TARGET_UNAVAILABLE");
        assertTerminal(outcome(1, false, false, true, true), "FAILED", "POLICY_VIOLATION");
    }

    @Test
    void manualStopCancelsSimulationAndFailsOpenRound() {
        SimulationRecord simulation = createSimulation(5, 2, Instant.now());
        SimulationRoundRecord round = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, Instant.now()));

        ResponseEntity<Object> response = controller.stop(simulation.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((SimulationResponse) response.getBody()).status()).isEqualTo("CANCELLED");
        assertThat(roundStore.findById(round.id()).orElseThrow().status()).isEqualTo("FAILED");
    }

    @Test
    void internalTransitionsRequireServiceTokenAndDisabledRetestStopsAfterRound() {
        SimulationRecord simulation = createSimulation(5, 2, Instant.now(), false);
        SimulationRoundRecord round = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, Instant.now()));

        assertThat(controller.advance(simulation.id(), round.id(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        advanceToScoring(simulation.id(), round.id());

        RoundCompletionResponse response = complete(simulation.id(), round.id(),
                outcome(1, false, false, true, false));
        assertThat(response.stopReason()).isEqualTo("RETEST_DISABLED");
        assertThat(response.nextRound()).isNull();
    }

    @Test
    void redTeamCompletionIsStageSpecificAndIdempotent() {
        SimulationRecord simulation = createSimulation(3, 2, Instant.now());
        SimulationRoundRecord round = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, Instant.now()));

        assertThat(controller.redTeamComplete(simulation.id(), round.id(), "service-token").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(advance(simulation.id(), round.id()).status()).isEqualTo("RED_TEAM_RUNNING");

        ResponseEntity<Object> first = controller.redTeamComplete(simulation.id(), round.id(), "service-token");
        ResponseEntity<Object> retry = controller.redTeamComplete(simulation.id(), round.id(), "service-token");

        assertThat(((SimulationRoundResponse) first.getBody()).status()).isEqualTo("DETECTION_RUNNING");
        assertThat(((SimulationRoundResponse) retry.getBody()).status()).isEqualTo("DETECTION_RUNNING");
        assertThat(outboxStore.findBySimulationId(simulation.id()).stream()
                .filter(event -> "simulation.round.detection.requested".equals(event.eventType())))
                .hasSize(1);
    }

    @Test
    void detectionCompletionIsStageSpecificAndIdempotent() {
        SimulationRecord simulation = createSimulation(3, 2, Instant.now());
        SimulationRoundRecord round = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, Instant.now()));
        advance(simulation.id(), round.id());
        controller.redTeamComplete(simulation.id(), round.id(), "service-token");

        ResponseEntity<Object> first = controller.detectionComplete(simulation.id(), round.id(), "service-token");
        ResponseEntity<Object> retry = controller.detectionComplete(simulation.id(), round.id(), "service-token");

        assertThat(((SimulationRoundResponse) first.getBody()).status()).isEqualTo("BLUE_TEAM_RUNNING");
        assertThat(((SimulationRoundResponse) retry.getBody()).status()).isEqualTo("BLUE_TEAM_RUNNING");
        assertThat(outboxStore.findBySimulationId(simulation.id()).stream()
                .filter(event -> "simulation.round.blue-team.requested".equals(event.eventType())))
                .hasSize(1);
    }

    @Test
    void blueTeamCompletionIsStageSpecificAndIdempotent() {
        SimulationRecord simulation = createSimulation(3, 2, Instant.now());
        SimulationRoundRecord round = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, Instant.now()));
        advance(simulation.id(), round.id());
        controller.redTeamComplete(simulation.id(), round.id(), "service-token");
        controller.detectionComplete(simulation.id(), round.id(), "service-token");

        ResponseEntity<Object> first = controller.blueTeamComplete(simulation.id(), round.id(), "service-token");
        ResponseEntity<Object> retry = controller.blueTeamComplete(simulation.id(), round.id(), "service-token");

        assertThat(((SimulationRoundResponse) first.getBody()).status()).isEqualTo("VERIFYING");
        assertThat(((SimulationRoundResponse) retry.getBody()).status()).isEqualTo("VERIFYING");
        assertThat(outboxStore.findBySimulationId(simulation.id()).stream()
                .filter(event -> "simulation.round.verification.requested".equals(event.eventType())))
                .hasSize(1);
    }

    @Test
    void verificationCompletionIsStageSpecificAndIdempotent() {
        SimulationRecord simulation = createSimulation(3, 2, Instant.now());
        SimulationRoundRecord round = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, Instant.now()));
        controller.advance(simulation.id(), round.id(), "service-token");
        controller.redTeamComplete(simulation.id(), round.id(), "service-token");
        controller.detectionComplete(simulation.id(), round.id(), "service-token");
        controller.blueTeamComplete(simulation.id(), round.id(), "service-token");

        ResponseEntity<Object> first = controller.verificationComplete(simulation.id(), round.id(), "service-token");
        ResponseEntity<Object> retry = controller.verificationComplete(simulation.id(), round.id(), "service-token");

        assertThat(((SimulationRoundResponse) first.getBody()).status()).isEqualTo("SCORING");
        assertThat(((SimulationRoundResponse) retry.getBody()).status()).isEqualTo("SCORING");
        assertThat(outboxStore.findBySimulationId(simulation.id()).stream()
                .filter(event -> "simulation.round.scoring.requested".equals(event.eventType())))
                .hasSize(1);
    }

    @Test
    void redTeamCompletionRequiresServiceToken() {
        SimulationRecord simulation = createSimulation(3, 2, Instant.now());
        SimulationRoundRecord round = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, Instant.now()));

        assertThat(controller.redTeamComplete(simulation.id(), round.id(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void assertTerminal(RoundCompletionRequest request, String status, String reason) {
        SimulationRecord simulation = createSimulation(5, 2, Instant.now());
        SimulationRoundRecord round = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, Instant.now()));
        advanceToScoring(simulation.id(), round.id());
        RoundCompletionResponse response = complete(simulation.id(), round.id(), request);
        assertThat(response.simulation().status()).isEqualTo(status);
        assertThat(response.stopReason()).isEqualTo(reason);
    }

    private SimulationRecord createSimulation(int maxRounds, int noFindingsThreshold, Instant startedAt) {
        return createSimulation(maxRounds, noFindingsThreshold, startedAt, true);
    }

    private SimulationRecord createSimulation(
            int maxRounds,
            int noFindingsThreshold,
            Instant startedAt,
            boolean retestEnabled
    ) {
        SimulationRecord simulation = new SimulationRecord(UUID.randomUUID(), "Round test", TargetMode.INTERNAL_SANDBOX,
                UUID.fromString("00000000-0000-0000-0000-000000000101"), "RUNNING", 1, maxRounds, 60,
                noFindingsThreshold, "LOW", retestEnabled, 0, 0, 0, 50, null,
                List.of("simulation.started", "round.1.created"), startedAt, null);
        return simulationStore.save(simulation);
    }

    private SimulationRoundResponse advance(UUID simulationId, UUID roundId) {
        return (SimulationRoundResponse) controller.advance(simulationId, roundId, "service-token").getBody();
    }

    private void advanceToScoring(UUID simulationId, UUID roundId) {
        controller.advance(simulationId, roundId, "service-token");
        controller.advance(simulationId, roundId, "service-token");
        controller.advance(simulationId, roundId, "service-token");
        controller.advance(simulationId, roundId, "service-token");
        controller.advance(simulationId, roundId, "service-token");
    }

    private RoundCompletionResponse complete(UUID simulationId, UUID roundId, RoundCompletionRequest request) {
        return (RoundCompletionResponse) controller.complete(simulationId, roundId, "service-token", request).getBody();
    }

    private RoundCompletionRequest outcome(int findings, boolean fixed, boolean unsafe, boolean available, boolean policy) {
        return new RoundCompletionRequest(findings, 1, 1, 50, 20, 100, 120,
                fixed, unsafe, available, policy);
    }

    private static final class InMemorySimulationStore implements SimulationStore {
        private final Map<UUID, SimulationRecord> records = new ConcurrentHashMap<>();
        public SimulationRecord save(SimulationRecord simulation) { records.put(simulation.id(), simulation); return simulation; }
        public Optional<SimulationRecord> findById(UUID id) { return Optional.ofNullable(records.get(id)); }
    }

    private static final class InMemoryRoundStore implements SimulationRoundStore {
        private final Map<UUID, SimulationRoundRecord> records = new ConcurrentHashMap<>();
        public SimulationRoundRecord save(SimulationRoundRecord round) { records.put(round.id(), round); return round; }
        public Optional<SimulationRoundRecord> findById(UUID id) { return Optional.ofNullable(records.get(id)); }
        public List<SimulationRoundRecord> findBySimulationId(UUID simulationId) {
            return records.values().stream().filter(round -> simulationId.equals(round.simulationId()))
                    .sorted(Comparator.comparingInt(SimulationRoundRecord::roundNumber)).toList();
        }
    }

    private static final class InMemoryOutboxStore implements OutboxStore {
        private final Map<UUID, OutboxEventRecord> records = new java.util.LinkedHashMap<>();
        public OutboxEventRecord save(OutboxEventRecord event) { records.put(event.id(), event); return event; }
        public List<OutboxEventRecord> findReady(Instant now) {
            return records.values().stream().filter(event -> !"PUBLISHED".equals(event.status()))
                    .filter(event -> !event.nextAttemptAt().isAfter(now)).toList();
        }
        public List<OutboxEventRecord> findBySimulationId(UUID simulationId) {
            return records.values().stream().filter(event -> simulationId.equals(event.simulationId())).toList();
        }
    }
}
