package com.cybersim.simulationorchestratorservice.store;

import com.cybersim.simulationorchestratorservice.model.SimulationRoundRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationRoundStore {
    SimulationRoundRecord save(SimulationRoundRecord round);

    Optional<SimulationRoundRecord> findById(UUID id);

    List<SimulationRoundRecord> findBySimulationId(UUID simulationId);
}
