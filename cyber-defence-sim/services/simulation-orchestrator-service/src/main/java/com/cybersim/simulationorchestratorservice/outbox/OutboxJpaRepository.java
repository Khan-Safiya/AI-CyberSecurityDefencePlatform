package com.cybersim.simulationorchestratorservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses,
            Instant nextAttemptAt
    );

    List<OutboxEventEntity> findBySimulationIdOrderByCreatedAtAsc(UUID simulationId);
}
