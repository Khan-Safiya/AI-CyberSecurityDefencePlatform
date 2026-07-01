package com.cybersim.remediationservice.persistence;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
public class ConsumedMessageStore {
    private final ConsumedMessageRepository repository;

    ConsumedMessageStore(ConsumedMessageRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean contains(UUID messageId) { return repository.existsById(messageId); }

    @Transactional
    public void record(UUID messageId) { repository.save(new ConsumedMessageEntity(messageId, Instant.now())); }
}
