package com.cybersim.simulationorchestratorservice.outbox;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional
class JpaOutboxStore implements OutboxStore {
    private static final List<String> READY_STATUSES = List.of("PENDING", "FAILED");
    private final OutboxJpaRepository repository;

    JpaOutboxStore(OutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public OutboxEventRecord save(OutboxEventRecord event) {
        return repository.save(OutboxEventEntity.from(event)).toRecord();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEventRecord> findReady(Instant now) {
        return repository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(READY_STATUSES, now)
                .stream().map(OutboxEventEntity::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEventRecord> findBySimulationId(UUID simulationId) {
        return repository.findBySimulationIdOrderByCreatedAtAsc(simulationId).stream()
                .map(OutboxEventEntity::toRecord).toList();
    }
}
