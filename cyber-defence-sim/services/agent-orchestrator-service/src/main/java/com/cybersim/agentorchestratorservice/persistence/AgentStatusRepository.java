package com.cybersim.agentorchestratorservice.persistence;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

interface AgentStatusRepository extends CrudRepository<AgentStatusEntity, UUID> {
    List<AgentStatusEntity> findBySimulationId(UUID simulationId);
}
