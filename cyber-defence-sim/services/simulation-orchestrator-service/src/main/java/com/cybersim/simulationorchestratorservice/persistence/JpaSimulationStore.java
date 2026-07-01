package com.cybersim.simulationorchestratorservice.persistence;

import com.cybersim.simulationorchestratorservice.model.SimulationRecord;
import com.cybersim.simulationorchestratorservice.store.SimulationStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
class JpaSimulationStore implements SimulationStore {
    private final SimulationJpaRepository repository;

    JpaSimulationStore(SimulationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public SimulationRecord save(SimulationRecord simulation) {
        return repository.save(SimulationEntity.from(simulation)).toRecord();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SimulationRecord> findById(UUID id) {
        return repository.findById(id).map(SimulationEntity::toRecord);
    }
}
