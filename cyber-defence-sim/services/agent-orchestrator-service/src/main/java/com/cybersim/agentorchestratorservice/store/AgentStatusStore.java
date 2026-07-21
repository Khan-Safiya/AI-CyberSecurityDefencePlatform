package com.cybersim.agentorchestratorservice.store;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AgentStatusStore {
    void upsert(UUID simulationId, String team, String agentName, String status, Instant updatedAt);
    List<AgentStatusRecord> findBySimulationId(UUID simulationId);

    record AgentStatusRecord(UUID simulationId, String team, String agentName, String status, Instant updatedAt) {
    }
}
