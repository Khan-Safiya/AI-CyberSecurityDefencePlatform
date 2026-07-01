package com.cybersim.remediationservice.store;

import com.cybersim.remediationservice.model.RemediationRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemediationStore {
    RemediationRecord save(RemediationRecord remediation);

    Optional<RemediationRecord> findById(UUID id);

    List<RemediationRecord> findBySimulationId(UUID simulationId);
}
