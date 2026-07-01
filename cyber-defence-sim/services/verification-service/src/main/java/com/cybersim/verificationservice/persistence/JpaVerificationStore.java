package com.cybersim.verificationservice.persistence;

import com.cybersim.verificationservice.model.VerificationRecord;
import com.cybersim.verificationservice.store.VerificationStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
class JpaVerificationStore implements VerificationStore {
    private final VerificationJpaRepository repository;

    JpaVerificationStore(VerificationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public VerificationRecord save(VerificationRecord verification) {
        return repository.save(VerificationEntity.from(verification)).toRecord();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerificationRecord> findById(UUID id) {
        return repository.findById(id).map(VerificationEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VerificationRecord> findBySimulationId(UUID simulationId) {
        return repository.findBySimulationIdOrderByVerifiedAtAscIdAsc(simulationId).stream()
                .map(VerificationEntity::toRecord).toList();
    }
}
