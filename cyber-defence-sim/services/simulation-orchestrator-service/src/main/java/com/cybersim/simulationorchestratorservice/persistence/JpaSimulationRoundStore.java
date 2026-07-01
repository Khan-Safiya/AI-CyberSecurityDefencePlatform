package com.cybersim.simulationorchestratorservice.persistence;

import com.cybersim.simulationorchestratorservice.model.SimulationRoundRecord;
import com.cybersim.simulationorchestratorservice.store.SimulationRoundStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
class JpaSimulationRoundStore implements SimulationRoundStore {
    private final SimulationRoundJpaRepository repository;

    JpaSimulationRoundStore(SimulationRoundJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public SimulationRoundRecord save(SimulationRoundRecord round) {
        return repository.save(SimulationRoundEntity.from(round)).toRecord();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SimulationRoundRecord> findById(UUID id) {
        return repository.findById(id).map(SimulationRoundEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulationRoundRecord> findBySimulationId(UUID simulationId) {
        return repository.findBySimulationIdOrderByRoundNumberAsc(simulationId).stream()
                .map(SimulationRoundEntity::toRecord).toList();
    }
}
