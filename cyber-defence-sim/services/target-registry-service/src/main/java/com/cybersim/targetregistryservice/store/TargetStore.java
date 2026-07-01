package com.cybersim.targetregistryservice.store;

import com.cybersim.targetregistryservice.model.TargetRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TargetStore {
    TargetRecord save(TargetRecord target);

    Optional<TargetRecord> findById(UUID id);

    List<TargetRecord> findAll();
}
