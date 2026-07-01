package com.cybersim.simulationorchestratorservice.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStore {
    OutboxEventRecord save(OutboxEventRecord event);

    List<OutboxEventRecord> findReady(Instant now);

    List<OutboxEventRecord> findBySimulationId(UUID simulationId);
}
