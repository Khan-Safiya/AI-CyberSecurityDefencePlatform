package com.cybersim.simulationorchestratorservice.store;

import com.cybersim.simulationorchestratorservice.model.SimulationRecord;

import java.util.Optional;
import java.util.UUID;

public interface SimulationStore {
    SimulationRecord save(SimulationRecord simulation);

    Optional<SimulationRecord> findById(UUID id);
}
