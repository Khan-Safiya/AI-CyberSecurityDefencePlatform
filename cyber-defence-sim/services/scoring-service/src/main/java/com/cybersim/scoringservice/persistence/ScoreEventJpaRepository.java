package com.cybersim.scoringservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ScoreEventJpaRepository extends JpaRepository<ScoreEventEntity, UUID> {
    Optional<ScoreEventEntity> findBySimulationIdAndSourceEventId(UUID simulationId, UUID sourceEventId);

    List<ScoreEventEntity> findBySimulationIdOrderByCreatedAtAscIdAsc(UUID simulationId);
}
