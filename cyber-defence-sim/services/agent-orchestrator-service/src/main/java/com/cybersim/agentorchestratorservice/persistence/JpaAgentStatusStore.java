package com.cybersim.agentorchestratorservice.persistence;

import com.cybersim.agentorchestratorservice.store.AgentStatusStore;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class JpaAgentStatusStore implements AgentStatusStore {
    private final AgentStatusRepository repository;

    JpaAgentStatusStore(AgentStatusRepository repository) {
        this.repository = repository;
    }

    @Override
    public void upsert(UUID simulationId, String team, String agentName, String status, Instant updatedAt) {
        UUID id = deterministicId(simulationId, team);
        AgentStatusEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            repository.save(new AgentStatusEntity(id, simulationId, team, agentName, status, updatedAt));
        } else {
            entity.update(agentName, status, updatedAt);
            repository.save(entity);
        }
    }

    @Override
    public List<AgentStatusStore.AgentStatusRecord> findBySimulationId(UUID simulationId) {
        return repository.findBySimulationId(simulationId).stream()
                .map(entity -> new AgentStatusStore.AgentStatusRecord(
                        entity.simulationId(), entity.team(), entity.agentName(), entity.status(), entity.updatedAt()))
                .toList();
    }

    private static UUID deterministicId(UUID simulationId, String team) {
        return UUID.nameUUIDFromBytes((simulationId + ":" + team).getBytes(StandardCharsets.UTF_8));
    }
}
