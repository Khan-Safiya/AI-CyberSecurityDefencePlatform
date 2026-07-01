package com.cybersim.detectionengineservice.store;

import com.cybersim.detectionengineservice.model.DetectionEventRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DetectionEventStore {
    DetectionEventRecord save(DetectionEventRecord event);

    Optional<DetectionEventRecord> findById(UUID id);

    List<DetectionEventRecord> findBySimulationId(UUID simulationId);
}
