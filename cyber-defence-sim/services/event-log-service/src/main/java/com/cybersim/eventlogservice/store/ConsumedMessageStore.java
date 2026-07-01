package com.cybersim.eventlogservice.store;

import java.time.Instant;
import java.util.UUID;

public interface ConsumedMessageStore {
    boolean contains(UUID messageId);

    void record(UUID messageId, String eventType, Instant consumedAt);
}
