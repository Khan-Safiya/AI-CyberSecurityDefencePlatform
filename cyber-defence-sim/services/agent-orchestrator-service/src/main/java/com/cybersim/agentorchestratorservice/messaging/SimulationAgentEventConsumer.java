package com.cybersim.agentorchestratorservice.messaging;

import com.cybersim.agentorchestratorservice.store.AgentStatusStore;
import com.cybersim.agentorchestratorservice.store.ConsumedMessageStore;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Projects the platform's simulation event stream into a per-simulation "which agent is doing
 * what right now" read model, replacing the previous hardcoded static agent list. This consumer
 * does not drive any workflow itself - it only reflects state that simulation-orchestrator-service
 * already owns and publishes.
 */
@Component
@ConditionalOnProperty(name = "agent-event-consumer.enabled", havingValue = "true", matchIfMissing = true)
public class SimulationAgentEventConsumer {
    static final String RED_TEAM = "RED";
    static final String BLUE_TEAM = "BLUE";
    static final String RED_TEAM_AGENT_NAME = "ReconAgent";
    static final String BLUE_TEAM_AGENT_NAME = "PatchAgent";

    private final AgentStatusStore agentStatusStore;
    private final ConsumedMessageStore consumedMessageStore;

    public SimulationAgentEventConsumer(AgentStatusStore agentStatusStore, ConsumedMessageStore consumedMessageStore) {
        this.agentStatusStore = agentStatusStore;
        this.consumedMessageStore = consumedMessageStore;
    }

    @RabbitListener(queues = RabbitAgentEventConsumerConfiguration.QUEUE_NAME)
    @Transactional
    public void consume(Message message) {
        UUID messageId = requiredUuid(message.getMessageProperties().getMessageId(), "messageId");
        if (consumedMessageStore.contains(messageId)) {
            return;
        }

        String eventType = requiredHeader(message, "eventType");
        UUID simulationId = requiredUuid(requiredHeader(message, "simulationId"), "simulationId");
        Instant now = Instant.now();

        switch (eventType) {
            case "simulation.round.created" -> {
                agentStatusStore.upsert(simulationId, RED_TEAM, RED_TEAM_AGENT_NAME, "PENDING", now);
                agentStatusStore.upsert(simulationId, BLUE_TEAM, BLUE_TEAM_AGENT_NAME, "PENDING", now);
            }
            case "simulation.round.red-team.requested" ->
                    agentStatusStore.upsert(simulationId, RED_TEAM, RED_TEAM_AGENT_NAME, "RUNNING", now);
            case "simulation.round.detection.requested" ->
                    agentStatusStore.upsert(simulationId, RED_TEAM, RED_TEAM_AGENT_NAME, "COMPLETED", now);
            case "simulation.round.blue-team.requested" ->
                    agentStatusStore.upsert(simulationId, BLUE_TEAM, BLUE_TEAM_AGENT_NAME, "RUNNING", now);
            case "simulation.round.verification.requested" ->
                    agentStatusStore.upsert(simulationId, BLUE_TEAM, BLUE_TEAM_AGENT_NAME, "COMPLETED", now);
            default -> {
                // Other event types (scoring, completion, cancellation, ...) do not change which
                // agent is currently active and are intentionally ignored.
            }
        }

        consumedMessageStore.record(messageId, eventType, now);
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
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid UUID in event field: " + name, exception);
        }
    }
}
