package com.cybersim.targetregistryservice.persistence;

import com.cybersim.targetregistryservice.model.TargetRecord;
import com.cybersim.targetregistryservice.store.TargetStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
class JpaTargetStore implements TargetStore {
    private final TargetJpaRepository repository;

    JpaTargetStore(TargetJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public TargetRecord save(TargetRecord target) {
        return repository.save(TargetEntity.from(target)).toRecord();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TargetRecord> findById(UUID id) {
        return repository.findById(id).map(TargetEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TargetRecord> findAll() {
        return repository.findAll().stream().map(TargetEntity::toRecord).toList();
    }
}
