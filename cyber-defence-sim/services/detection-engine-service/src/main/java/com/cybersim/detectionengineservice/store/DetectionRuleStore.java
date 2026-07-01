package com.cybersim.detectionengineservice.store;

import com.cybersim.detectionengineservice.model.DetectionRuleRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DetectionRuleStore {
    DetectionRuleRecord save(DetectionRuleRecord rule);

    Optional<DetectionRuleRecord> findById(UUID id);

    List<DetectionRuleRecord> findAll();

    void deleteById(UUID id);
}
