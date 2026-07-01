package com.cybersim.simulationorchestratorservice.persistence;

import com.cybersim.shared.dto.TargetMode;
import com.cybersim.simulationorchestratorservice.model.SimulationRecord;
import com.cybersim.simulationorchestratorservice.model.SimulationRoundRecord;
import com.cybersim.simulationorchestratorservice.store.SimulationRoundStore;
import com.cybersim.simulationorchestratorservice.store.SimulationStore;
import com.cybersim.simulationorchestratorservice.outbox.OutboxEventFactory;
import com.cybersim.simulationorchestratorservice.outbox.OutboxEventRecord;
import com.cybersim.simulationorchestratorservice.outbox.OutboxStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SimulationPersistenceIntegrationTest {
    @Autowired
    private SimulationStore simulationStore;
    @Autowired
    private SimulationRoundStore roundStore;
    @Autowired
    private OutboxStore outboxStore;
    @Autowired
    private OutboxEventFactory outboxEventFactory;

    @Test
    void flywaySchemaPersistsCompleteSimulationState() {
        Instant now = Instant.now();
        SimulationRecord simulation = new SimulationRecord(
                UUID.randomUUID(),
                "Persistent simulation",
                TargetMode.INTERNAL_SANDBOX,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "RUNNING",
                1,
                5,
                60,
                2,
                "LOW",
                true,
                10,
                0,
                0,
                50,
                null,
                List.of("simulation.started", "round.1.created"),
                now,
                null
        );

        simulationStore.save(simulation);
        SimulationRoundRecord round = roundStore.save(SimulationRoundRecord.create(simulation.id(), 1, now));
        SimulationRecord reloaded = simulationStore.findById(simulation.id()).orElseThrow();

        assertThat(reloaded.id()).isEqualTo(simulation.id());
        assertThat(reloaded.name()).isEqualTo("Persistent simulation");
        assertThat(reloaded.targetId()).isEqualTo(simulation.targetId());
        assertThat(reloaded.maxRounds()).isEqualTo(5);
        assertThat(reloaded.maxDurationMinutes()).isEqualTo(60);
        assertThat(reloaded.stopWhenNoNewFindingsForRounds()).isEqualTo(2);
        assertThat(reloaded.minimumAcceptedRiskLevel()).isEqualTo("LOW");
        assertThat(reloaded.retestEnabled()).isTrue();
        assertThat(reloaded.timeline()).containsExactly("simulation.started", "round.1.created");
        assertThat(reloaded.endedAt()).isNull();
        assertThat(roundStore.findById(round.id())).get().extracting(SimulationRoundRecord::status).isEqualTo("CREATED");
    }

    @Test
    void persistsPendingOutboxEventWithRoundState() {
        UUID simulationId = UUID.randomUUID();
        OutboxEventRecord event = outboxStore.save(outboxEventFactory.create(
                "simulation.round.red-team.requested", simulationId, UUID.randomUUID(),
                Map.of("roundNumber", 1, "status", "RED_TEAM_RUNNING")));

        assertThat(outboxStore.findReady(Instant.now().plusSeconds(1)))
                .extracting(OutboxEventRecord::id).contains(event.id());
        assertThat(outboxStore.findBySimulationId(simulationId)).singleElement().satisfies(stored -> {
            assertThat(stored.status()).isEqualTo("PENDING");
            assertThat(stored.payloadJson()).contains("roundNumber", "simulationId");
        });
    }
}
