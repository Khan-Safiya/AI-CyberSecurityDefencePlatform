package com.cybersim.verificationservice.messaging;

import com.cybersim.verificationservice.persistence.ConsumedMessageStore;
import com.cybersim.verificationservice.workflow.VerificationStageProcessor;
import com.cybersim.verificationservice.workflow.VerificationWorkflowClient;
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

class VerificationStageConsumerTest {
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @Test
    void duplicateDeliveryReleasesScoringOnce() {
        ConsumedMessageStore inbox = mock(ConsumedMessageStore.class);
        VerificationStageProcessor processor = mock(VerificationStageProcessor.class);
        VerificationWorkflowClient workflow = mock(VerificationWorkflowClient.class);
        when(inbox.contains(MESSAGE_ID)).thenReturn(false, true);
        when(workflow.findRemediations(SIMULATION_ID)).thenReturn(List.of());
        VerificationStageConsumer consumer = new VerificationStageConsumer(inbox, processor, workflow, new ObjectMapper());
        Message message = message();

        consumer.consume(message);
        consumer.consume(message);

        verify(processor, times(1)).verify(MESSAGE_ID, SIMULATION_ID, ROUND_ID, List.of());
        verify(workflow, times(1)).completeVerificationStage(SIMULATION_ID, ROUND_ID);
        verify(inbox, times(1)).record(MESSAGE_ID);
    }

    private Message message() {
        return MessageBuilder.withBody(("{\"roundId\":\"" + ROUND_ID + "\"}")
                .getBytes(StandardCharsets.UTF_8)).setMessageId(MESSAGE_ID.toString())
                .setHeader("eventType", VerificationRabbitConfiguration.ROUTING_KEY)
                .setHeader("simulationId", SIMULATION_ID.toString()).build();
    }
}
