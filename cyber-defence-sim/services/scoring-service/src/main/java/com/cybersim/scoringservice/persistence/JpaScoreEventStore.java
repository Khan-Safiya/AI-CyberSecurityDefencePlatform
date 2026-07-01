package com.cybersim.scoringservice.persistence;

import com.cybersim.scoringservice.model.ScoreEventRecord;
import com.cybersim.scoringservice.store.ScoreAppendResult;
import com.cybersim.scoringservice.store.ScoreEventStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
class JpaScoreEventStore implements ScoreEventStore {
    private final ScoreEventJpaRepository repository;

    JpaScoreEventStore(ScoreEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ScoreAppendResult append(ScoreEventRecord event) {
        Optional<ScoreEventEntity> existing = repository
                .findBySimulationIdAndSourceEventId(event.simulationId(), event.sourceEventId());
        if (existing.isPresent()) {
            return new ScoreAppendResult(existing.get().toRecord(), false);
        }
        return new ScoreAppendResult(repository.save(ScoreEventEntity.from(event)).toRecord(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScoreEventRecord> findBySimulationIdAndSourceEventId(UUID simulationId, UUID sourceEventId) {
        return repository.findBySimulationIdAndSourceEventId(simulationId, sourceEventId).map(ScoreEventEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoreEventRecord> findBySimulationId(UUID simulationId) {
        return repository.findBySimulationIdOrderByCreatedAtAscIdAsc(simulationId).stream()
                .map(ScoreEventEntity::toRecord).toList();
    }
}
