package com.cybersim.redteamagentservice.persistence;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class ConsumedMessageStore {
    private final ConsumedMessageRepository repository;

    ConsumedMessageStore(ConsumedMessageRepository repository) {
        this.repository = repository;
    }

    public boolean contains(UUID messageId) {
        return repository.existsById(messageId);
    }

    public void record(UUID messageId) {
        repository.save(new ConsumedMessageEntity(messageId, Instant.now()));
    }
}
