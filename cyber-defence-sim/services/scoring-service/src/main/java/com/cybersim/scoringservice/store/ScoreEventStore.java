package com.cybersim.scoringservice.store;

import com.cybersim.scoringservice.model.ScoreEventRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreEventStore {
    ScoreAppendResult append(ScoreEventRecord event);

    Optional<ScoreEventRecord> findBySimulationIdAndSourceEventId(UUID simulationId, UUID sourceEventId);

    List<ScoreEventRecord> findBySimulationId(UUID simulationId);
}
