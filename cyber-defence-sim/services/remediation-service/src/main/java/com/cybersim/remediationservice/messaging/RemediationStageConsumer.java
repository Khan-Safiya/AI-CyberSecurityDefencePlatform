package com.cybersim.remediationservice.messaging;

import com.cybersim.remediationservice.persistence.ConsumedMessageStore;
import com.cybersim.remediationservice.workflow.RemediationStageProcessor;
import com.cybersim.remediationservice.workflow.RemediationWorkflowClient;
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
@ConditionalOnProperty(name = "remediation.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class RemediationStageConsumer {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() { };
    private final ConsumedMessageStore inbox;
    private final RemediationStageProcessor processor;
    private final RemediationWorkflowClient workflowClient;
    private final ObjectMapper objectMapper;

    public RemediationStageConsumer(ConsumedMessageStore inbox, RemediationStageProcessor processor,
                                    RemediationWorkflowClient workflowClient, ObjectMapper objectMapper) {
        this.inbox = inbox;
        this.processor = processor;
        this.workflowClient = workflowClient;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RemediationRabbitConfiguration.QUEUE)
    public void consume(Message message) {
        UUID messageId = uuid(message.getMessageProperties().getMessageId(), "messageId");
        if (inbox.contains(messageId)) return;
        String eventType = header(message, "eventType");
        if (!RemediationRabbitConfiguration.ROUTING_KEY.equals(eventType)) {
            throw new IllegalArgumentException("Unexpected remediation event type: " + eventType);
        }
        UUID simulationId = uuid(header(message, "simulationId"), "simulationId");
        UUID roundId = uuid(String.valueOf(payload(message).get("roundId")), "roundId");
        RemediationStageProcessor.BatchResult result = processor.persistAndApply(messageId, simulationId, roundId,
                workflowClient.findings(simulationId), workflowClient.detections(simulationId));
        if (!result.successful()) {
            throw new IllegalStateException("One or more sandbox remediations failed to apply");
        }
        workflowClient.completeBlueTeamStage(simulationId, roundId);
        inbox.record(messageId);
    }

    private Map<String, Object> payload(Message message) {
        try { return objectMapper.readValue(message.getBody(), PAYLOAD_TYPE); }
        catch (IOException exception) { throw new IllegalArgumentException("Remediation payload is not valid JSON", exception); }
    }
    private String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("Missing remediation header: " + name);
        return value.toString();
    }
    private UUID uuid(String value, String name) {
        if (value == null || value.isBlank() || "null".equals(value)) throw new IllegalArgumentException("Missing remediation field: " + name);
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid remediation UUID: " + name, exception); }
    }
}
