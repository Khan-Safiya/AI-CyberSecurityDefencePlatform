package com.cybersim.detectionengineservice.messaging;

import com.cybersim.detectionengineservice.persistence.ConsumedMessageStore;
import com.cybersim.detectionengineservice.workflow.DetectionStageProcessor;
import com.cybersim.detectionengineservice.workflow.DetectionWorkflowClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetectionStageConsumerTest {
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @Test
    void duplicateDeliveryPersistsAndCompletesOnce() {
        ConsumedMessageStore inbox = mock(ConsumedMessageStore.class);
        DetectionStageProcessor processor = mock(DetectionStageProcessor.class);
        DetectionWorkflowClient client = mock(DetectionWorkflowClient.class);
        when(inbox.contains(MESSAGE_ID)).thenReturn(false, true);
        when(client.findings(SIMULATION_ID)).thenReturn(List.of());
        DetectionStageConsumer consumer = new DetectionStageConsumer(inbox, processor, client, new ObjectMapper());
        Message message = message();

        consumer.consume(message);
        consumer.consume(message);

        verify(processor, times(1)).persist(MESSAGE_ID, SIMULATION_ID, ROUND_ID, List.of());
        verify(client, times(1)).completeDetectionStage(SIMULATION_ID, ROUND_ID);
        verify(inbox, times(1)).record(MESSAGE_ID);
    }

    private Message message() {
        return MessageBuilder.withBody(("{\"roundId\":\"" + ROUND_ID + "\"}")
                        .getBytes(StandardCharsets.UTF_8))
                .setMessageId(MESSAGE_ID.toString())
                .setHeader("eventType", DetectionRabbitConfiguration.ROUTING_KEY)
                .setHeader("simulationId", SIMULATION_ID.toString())
                .build();
    }
}
