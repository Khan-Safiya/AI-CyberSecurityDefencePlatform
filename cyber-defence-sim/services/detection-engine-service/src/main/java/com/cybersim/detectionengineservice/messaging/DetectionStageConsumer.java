package com.cybersim.detectionengineservice.messaging;

import com.cybersim.detectionengineservice.persistence.ConsumedMessageStore;
import com.cybersim.detectionengineservice.workflow.DetectionStageProcessor;
import com.cybersim.detectionengineservice.workflow.DetectionWorkflowClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "detection.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class DetectionStageConsumer {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() { };

    private final ConsumedMessageStore inbox;
    private final DetectionStageProcessor processor;
    private final DetectionWorkflowClient workflowClient;
    private final ObjectMapper objectMapper;

    public DetectionStageConsumer(ConsumedMessageStore inbox, DetectionStageProcessor processor,
                                  DetectionWorkflowClient workflowClient, ObjectMapper objectMapper) {
        this.inbox = inbox;
        this.processor = processor;
        this.workflowClient = workflowClient;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = DetectionRabbitConfiguration.QUEUE)
    public void consume(Message message) {
        UUID messageId = uuid(message.getMessageProperties().getMessageId(), "messageId");
        if (inbox.contains(messageId)) {
            return;
        }
        String eventType = header(message, "eventType");
        if (!DetectionRabbitConfiguration.ROUTING_KEY.equals(eventType)) {
            throw new IllegalArgumentException("Unexpected detection event type: " + eventType);
        }
        UUID simulationId = uuid(header(message, "simulationId"), "simulationId");
        UUID roundId = uuid(String.valueOf(payload(message).get("roundId")), "roundId");

        processor.persist(messageId, simulationId, roundId, workflowClient.findings(simulationId));
        workflowClient.completeDetectionStage(simulationId, roundId);
        inbox.record(messageId);
    }

    private Map<String, Object> payload(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), PAYLOAD_TYPE);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Detection event payload is not valid JSON", exception);
        }
    }

    private String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing detection event header: " + name);
        }
        return value.toString();
    }

    private UUID uuid(String value, String name) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            throw new IllegalArgumentException("Missing detection event field: " + name);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid UUID in detection event field: " + name, exception);
        }
    }
}
