package com.cybersim.redteamagentservice.messaging;

import com.cybersim.redteamagentservice.persistence.ConsumedMessageStore;
import com.cybersim.redteamagentservice.workflow.RedTeamStageProcessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "red-team.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class RedTeamStageConsumer {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() { };

    private final ConsumedMessageStore inbox;
    private final RedTeamStageProcessor processor;
    private final ObjectMapper objectMapper;

    public RedTeamStageConsumer(ConsumedMessageStore inbox, RedTeamStageProcessor processor, ObjectMapper objectMapper) {
        this.inbox = inbox;
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RedTeamRabbitConfiguration.QUEUE)
    @Transactional
    public void consume(Message message) {
        UUID messageId = uuid(message.getMessageProperties().getMessageId(), "messageId");
        if (inbox.contains(messageId)) {
            return;
        }
        String eventType = header(message, "eventType");
        if (!RedTeamRabbitConfiguration.ROUTING_KEY.equals(eventType)) {
            throw new IllegalArgumentException("Unexpected red-team event type: " + eventType);
        }
        UUID simulationId = uuid(header(message, "simulationId"), "simulationId");
        Map<String, Object> payload = payload(message);
        UUID roundId = uuid(String.valueOf(payload.get("roundId")), "roundId");

        processor.process(messageId, simulationId, roundId);
        inbox.record(messageId);
    }

    private Map<String, Object> payload(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), PAYLOAD_TYPE);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Red-team event payload is not valid JSON", exception);
        }
    }

    private String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing red-team event header: " + name);
        }
        return value.toString();
    }

    private UUID uuid(String value, String name) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            throw new IllegalArgumentException("Missing red-team event field: " + name);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid UUID in red-team event field: " + name, exception);
        }
    }
}
