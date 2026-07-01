package com.cybersim.simulationorchestratorservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SimulationJpaRepository extends JpaRepository<SimulationEntity, UUID> {
}
