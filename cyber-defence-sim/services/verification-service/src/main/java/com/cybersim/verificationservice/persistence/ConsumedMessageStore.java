package com.cybersim.verificationservice.persistence;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Repository
public class ConsumedMessageStore {
    private final ConsumedMessageRepository repository;
    ConsumedMessageStore(ConsumedMessageRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true) public boolean contains(UUID id) { return repository.existsById(id); }
    @Transactional public void record(UUID id) { repository.save(new ConsumedMessageEntity(id, Instant.now())); }
}
