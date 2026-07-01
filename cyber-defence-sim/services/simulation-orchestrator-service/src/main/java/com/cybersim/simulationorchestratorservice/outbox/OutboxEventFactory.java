package com.cybersim.simulationorchestratorservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxEventFactory {
    private final ObjectMapper objectMapper;

    public OutboxEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEventRecord create(
            String eventType,
            UUID simulationId,
            UUID roundId,
            Map<String, Object> payload
    ) {
        Map<String, Object> safePayload = new LinkedHashMap<>(payload);
        safePayload.put("simulationId", simulationId.toString());
        if (roundId != null) {
            safePayload.put("roundId", roundId.toString());
        }
        try {
            return OutboxEventRecord.pending(eventType, eventType, simulationId, roundId,
                    objectMapper.writeValueAsString(safePayload), Instant.now());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Outbox payload cannot be serialized", exception);
        }
    }
}
