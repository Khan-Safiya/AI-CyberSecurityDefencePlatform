package com.cybersim.verificationservice.messaging;

import com.cybersim.verificationservice.persistence.ConsumedMessageStore;
import com.cybersim.verificationservice.workflow.VerificationStageProcessor;
import com.cybersim.verificationservice.workflow.VerificationWorkflowClient;
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
@ConditionalOnProperty(name = "verification.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class VerificationStageConsumer {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() { };
    private final ConsumedMessageStore inbox;
    private final VerificationStageProcessor processor;
    private final VerificationWorkflowClient workflowClient;
    private final ObjectMapper objectMapper;

    public VerificationStageConsumer(ConsumedMessageStore inbox, VerificationStageProcessor processor,
                                     VerificationWorkflowClient workflowClient, ObjectMapper objectMapper) {
        this.inbox = inbox;
        this.processor = processor;
        this.workflowClient = workflowClient;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = VerificationRabbitConfiguration.QUEUE)
    public void consume(Message message) {
        UUID messageId = uuid(message.getMessageProperties().getMessageId(), "messageId");
        if (inbox.contains(messageId)) return;
        String eventType = header(message, "eventType");
        if (!VerificationRabbitConfiguration.ROUTING_KEY.equals(eventType)) {
            throw new IllegalArgumentException("Unexpected verification event type: " + eventType);
        }
        UUID simulationId = uuid(header(message, "simulationId"), "simulationId");
        UUID roundId = uuid(String.valueOf(payload(message).get("roundId")), "roundId");
        processor.verify(messageId, simulationId, roundId, workflowClient.findRemediations(simulationId));
        workflowClient.completeVerificationStage(simulationId, roundId);
        inbox.record(messageId);
    }

    private Map<String, Object> payload(Message message) {
        try { return objectMapper.readValue(message.getBody(), PAYLOAD_TYPE); }
        catch (IOException e) { throw new IllegalArgumentException("Verification payload is not valid JSON", e); }
    }
    private String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("Missing verification header: " + name);
        return value.toString();
    }
    private UUID uuid(String value, String name) {
        if (value == null || value.isBlank() || "null".equals(value)) throw new IllegalArgumentException("Missing verification field: " + name);
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid verification UUID: " + name, e); }
    }
}
