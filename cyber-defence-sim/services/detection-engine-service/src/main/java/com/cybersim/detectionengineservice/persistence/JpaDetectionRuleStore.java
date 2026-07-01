package com.cybersim.detectionengineservice.persistence;

import com.cybersim.detectionengineservice.model.DetectionRuleRecord;
import com.cybersim.detectionengineservice.store.DetectionRuleStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
class JpaDetectionRuleStore implements DetectionRuleStore {
    private final DetectionRuleJpaRepository repository;

    JpaDetectionRuleStore(DetectionRuleJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public DetectionRuleRecord save(DetectionRuleRecord rule) {
        return repository.save(DetectionRuleEntity.from(rule)).toRecord();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DetectionRuleRecord> findById(UUID id) {
        return repository.findById(id).map(DetectionRuleEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DetectionRuleRecord> findAll() {
        return repository.findAllByOrderByCreatedAtAscIdAsc().stream().map(DetectionRuleEntity::toRecord).toList();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
