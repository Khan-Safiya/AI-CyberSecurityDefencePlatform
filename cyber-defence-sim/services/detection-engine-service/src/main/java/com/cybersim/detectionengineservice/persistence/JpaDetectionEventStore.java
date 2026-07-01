package com.cybersim.detectionengineservice.persistence;

import com.cybersim.detectionengineservice.model.DetectionEventRecord;
import com.cybersim.detectionengineservice.store.DetectionEventStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
class JpaDetectionEventStore implements DetectionEventStore {
    private final DetectionEventJpaRepository repository;

    JpaDetectionEventStore(DetectionEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public DetectionEventRecord save(DetectionEventRecord event) {
        return repository.save(DetectionEventEntity.from(event)).toRecord();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DetectionEventRecord> findById(UUID id) {
        return repository.findById(id).map(DetectionEventEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DetectionEventRecord> findBySimulationId(UUID simulationId) {
        return repository.findBySimulationIdOrderByCreatedAtAscIdAsc(simulationId).stream()
                .map(DetectionEventEntity::toRecord).toList();
    }
}
