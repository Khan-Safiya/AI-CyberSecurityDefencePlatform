package com.cybersim.simulationorchestratorservice.controller;

import com.cybersim.shared.dto.ApiErrorResponse;
import com.cybersim.shared.dto.SimulationRequest;
import com.cybersim.shared.dto.SimulationResponse;
import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.exceptions.GlobalApiExceptionHandler;
import com.cybersim.shared.observability.CorrelationIdFilter;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SimulationControllerTest {
    private SimulationController controller;
    private InMemoryRoundStore roundStore;
    private InMemoryOutboxStore outboxStore;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        roundStore = new InMemoryRoundStore();
        outboxStore = new InMemoryOutboxStore();
        controller = new SimulationController(new InMemorySimulationStore(), roundStore, outboxStore,
                new OutboxEventFactory(new ObjectMapper()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void startCreatesRunningSimulationAndDurableFirstRound() {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        SimulationRequest request = new SimulationRequest(
                "Baseline Web Application Defence Simulation",
                TargetMode.INTERNAL_SANDBOX,
                targetId,
                5,
                60,
                2
        );

        ResponseEntity<SimulationResponse> created = controller.start(null, request);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().status()).isEqualTo("RUNNING");
        assertThat(created.getBody().targetId()).isEqualTo(targetId);
        assertThat(created.getBody().timeline()).containsExactly("simulation.started", "round.1.created");
        assertThat(created.getBody().endedAt()).isNull();
        assertThat(roundStore.findBySimulationId(created.getBody().id()))
                .singleElement().extracting(SimulationRoundRecord::status).isEqualTo("CREATED");
        assertThat(outboxStore.findBySimulationId(created.getBody().id()))
                .extracting(OutboxEventRecord::eventType)
                .containsExactly("simulation.started", "simulation.round.created");
        assertThat(controller.get(created.getBody().id()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void missingSimulationReturnsStandardError() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000009997");

        ResponseEntity<Object> response = controller.get(simulationId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.message()).isNotBlank();
        assertThat(body.path()).isEqualTo("/simulations/" + simulationId);
    }

    @Test
    void defaultScenarioListsSixBaselineVulnerabilities() {
        Map<String, Object> scenario = controller.defaultScenario();

        assertThat(scenario).containsEntry("mode", "INTERNAL_SANDBOX");
        assertThat((Iterable<?>) scenario.get("vulnerabilities")).hasSize(6);
    }

    @Test
    void invalidSimulationLimitsAreRejected() throws Exception {
        mockMvc.perform(post("/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Invalid simulation",
                                  "mode": "INTERNAL_SANDBOX",
                                  "targetId": "00000000-0000-0000-0000-000000000101",
                                  "maxRounds": 2,
                                  "maxDurationMinutes": 60,
                                  "stopWhenNoNewFindingsForRounds": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    private static final class InMemorySimulationStore implements SimulationStore {
        private final Map<UUID, SimulationRecord> simulations = new ConcurrentHashMap<>();

        @Override
        public SimulationRecord save(SimulationRecord simulation) {
            simulations.put(simulation.id(), simulation);
            return simulation;
        }

        @Override
        public Optional<SimulationRecord> findById(UUID id) {
            return Optional.ofNullable(simulations.get(id));
        }
    }

    private static final class InMemoryRoundStore implements SimulationRoundStore {
        private final Map<UUID, SimulationRoundRecord> rounds = new ConcurrentHashMap<>();

        public SimulationRoundRecord save(SimulationRoundRecord round) {
            rounds.put(round.id(), round);
            return round;
        }

        public Optional<SimulationRoundRecord> findById(UUID id) {
            return Optional.ofNullable(rounds.get(id));
        }

        public List<SimulationRoundRecord> findBySimulationId(UUID simulationId) {
            return rounds.values().stream().filter(round -> simulationId.equals(round.simulationId()))
                    .sorted(java.util.Comparator.comparingInt(SimulationRoundRecord::roundNumber)).toList();
        }
    }

    private static final class InMemoryOutboxStore implements OutboxStore {
        private final Map<UUID, OutboxEventRecord> events = new java.util.LinkedHashMap<>();
        public OutboxEventRecord save(OutboxEventRecord event) { events.put(event.id(), event); return event; }
        public List<OutboxEventRecord> findReady(Instant now) {
            return events.values().stream().filter(event -> !"PUBLISHED".equals(event.status()))
                    .filter(event -> !event.nextAttemptAt().isAfter(now)).toList();
        }
        public List<OutboxEventRecord> findBySimulationId(UUID simulationId) {
            return events.values().stream().filter(event -> simulationId.equals(event.simulationId())).toList();
        }
    }
}
