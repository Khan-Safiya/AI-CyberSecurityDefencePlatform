package com.cybersim.remediationservice.messaging;

import com.cybersim.remediationservice.persistence.ConsumedMessageStore;
import com.cybersim.remediationservice.workflow.RemediationStageProcessor;
import com.cybersim.remediationservice.workflow.RemediationWorkflowClient;
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

class RemediationStageConsumerTest {
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @Test
    void duplicateDeliveryCompletesBlueTeamOnce() {
        ConsumedMessageStore inbox = mock(ConsumedMessageStore.class);
        RemediationStageProcessor processor = mock(RemediationStageProcessor.class);
        RemediationWorkflowClient workflow = mock(RemediationWorkflowClient.class);
        when(inbox.contains(MESSAGE_ID)).thenReturn(false, true);
        when(workflow.findings(SIMULATION_ID)).thenReturn(List.of());
        when(workflow.detections(SIMULATION_ID)).thenReturn(List.of());
        when(processor.persistAndApply(MESSAGE_ID, SIMULATION_ID, ROUND_ID, List.of(), List.of()))
                .thenReturn(new RemediationStageProcessor.BatchResult(0, true));
        RemediationStageConsumer consumer = new RemediationStageConsumer(inbox, processor, workflow, new ObjectMapper());
        Message message = message();

        consumer.consume(message);
        consumer.consume(message);

        verify(workflow, times(1)).completeBlueTeamStage(SIMULATION_ID, ROUND_ID);
        verify(inbox, times(1)).record(MESSAGE_ID);
    }

    private Message message() {
        return MessageBuilder.withBody(("{\"roundId\":\"" + ROUND_ID + "\"}")
                        .getBytes(StandardCharsets.UTF_8))
                .setMessageId(MESSAGE_ID.toString())
                .setHeader("eventType", RemediationRabbitConfiguration.ROUTING_KEY)
                .setHeader("simulationId", SIMULATION_ID.toString())
                .build();
    }
}
