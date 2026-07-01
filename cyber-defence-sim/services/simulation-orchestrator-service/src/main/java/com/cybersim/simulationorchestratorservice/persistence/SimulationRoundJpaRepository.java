package com.cybersim.simulationorchestratorservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SimulationRoundJpaRepository extends JpaRepository<SimulationRoundEntity, UUID> {
    List<SimulationRoundEntity> findBySimulationIdOrderByRoundNumberAsc(UUID simulationId);
}
