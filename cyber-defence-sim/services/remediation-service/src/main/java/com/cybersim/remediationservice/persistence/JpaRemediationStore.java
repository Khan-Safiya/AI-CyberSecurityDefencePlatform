package com.cybersim.remediationservice.persistence;

import com.cybersim.remediationservice.model.RemediationRecord;
import com.cybersim.remediationservice.store.RemediationStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
class JpaRemediationStore implements RemediationStore {
    private final RemediationJpaRepository repository;

    JpaRemediationStore(RemediationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public RemediationRecord save(RemediationRecord remediation) {
        return repository.save(RemediationEntity.from(remediation)).toRecord();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RemediationRecord> findById(UUID id) {
        return repository.findById(id).map(RemediationEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RemediationRecord> findBySimulationId(UUID simulationId) {
        return repository.findBySimulationIdOrderByCreatedAtAscIdAsc(simulationId).stream()
                .map(RemediationEntity::toRecord).toList();
    }
}
