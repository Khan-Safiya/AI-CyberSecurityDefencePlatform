package com.cybersim.agentorchestratorservice.persistence;

import com.cybersim.agentorchestratorservice.store.ConsumedMessageStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
class JpaConsumedMessageStore implements ConsumedMessageStore {
    private final ConsumedMessageRepository repository;

    JpaConsumedMessageStore(ConsumedMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean contains(UUID messageId) {
        return repository.existsById(messageId);
    }

    @Override
    public void record(UUID messageId, String eventType, Instant consumedAt) {
        repository.save(new ConsumedMessageEntity(messageId, eventType, consumedAt));
    }
}
