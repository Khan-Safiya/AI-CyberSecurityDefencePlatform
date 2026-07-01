package com.cybersim.eventlogservice.messaging;

import com.cybersim.eventlogservice.store.ConsumedMessageStore;
import com.cybersim.eventlogservice.store.EventStore;
import com.cybersim.shared.events.PlatformEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "event-consumer.enabled", havingValue = "true", matchIfMissing = true)
public class SimulationEventConsumer {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final EventStore eventStore;
    private final ConsumedMessageStore consumedMessageStore;
    private final ObjectMapper objectMapper;

    public SimulationEventConsumer(
            EventStore eventStore,
            ConsumedMessageStore consumedMessageStore,
            ObjectMapper objectMapper
    ) {
        this.eventStore = eventStore;
        this.consumedMessageStore = consumedMessageStore;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitEventConsumerConfiguration.QUEUE_NAME)
    @Transactional
    public void consume(Message message) {
        UUID messageId = requiredUuid(message.getMessageProperties().getMessageId(), "messageId");
        if (consumedMessageStore.contains(messageId)) {
            return;
        }

        String eventType = requiredHeader(message, "eventType");
        UUID simulationId = requiredUuid(requiredHeader(message, "simulationId"), "simulationId");
        Map<String, Object> payload = readPayload(message);
        UUID roundId = optionalUuid(payload.get("roundId"), "roundId");
        Instant consumedAt = Instant.now();

        eventStore.append(new PlatformEvent(messageId, eventType, simulationId, roundId, null,
                "simulation-orchestrator-service", messageId.toString(), consumedAt, payload));
        consumedMessageStore.record(messageId, eventType, consumedAt);
    }

    private Map<String, Object> readPayload(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), PAYLOAD_TYPE);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Event payload is not valid JSON", exception);
        }
    }

    private String requiredHeader(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing event header: " + name);
        }
        return value.toString();
    }

    private UUID requiredUuid(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing event field: " + name);
        }
        return parseUuid(value, name);
    }

    private UUID optionalUuid(Object value, String name) {
        return value == null ? null : parseUuid(value.toString(), name);
    }

    private UUID parseUuid(String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid UUID in event field: " + name, exception);
        }
    }
}
