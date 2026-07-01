package com.cybersim.verificationservice.store;

import com.cybersim.verificationservice.model.VerificationRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationStore {
    VerificationRecord save(VerificationRecord verification);

    Optional<VerificationRecord> findById(UUID id);

    List<VerificationRecord> findBySimulationId(UUID simulationId);
}
