package com.cybersim.detectionengineservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface DetectionEventJpaRepository extends JpaRepository<DetectionEventEntity, UUID> {
    List<DetectionEventEntity> findBySimulationIdOrderByCreatedAtAscIdAsc(UUID simulationId);
}
