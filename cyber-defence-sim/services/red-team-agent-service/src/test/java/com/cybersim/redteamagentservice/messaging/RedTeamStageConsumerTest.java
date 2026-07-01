package com.cybersim.redteamagentservice.messaging;

import com.cybersim.redteamagentservice.persistence.ConsumedMessageStore;
import com.cybersim.redteamagentservice.workflow.RedTeamStageProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedTeamStageConsumerTest {
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @Test
    void duplicateDeliveryRunsWorkflowOnce() {
        ConsumedMessageStore inbox = mock(ConsumedMessageStore.class);
        RedTeamStageProcessor processor = mock(RedTeamStageProcessor.class);
        when(inbox.contains(MESSAGE_ID)).thenReturn(false, true);
        RedTeamStageConsumer consumer = new RedTeamStageConsumer(inbox, processor, new ObjectMapper());
        Message message = message(RedTeamRabbitConfiguration.ROUTING_KEY);

        consumer.consume(message);
        consumer.consume(message);

        verify(processor, times(1)).process(MESSAGE_ID, SIMULATION_ID, ROUND_ID);
        verify(inbox, times(1)).record(MESSAGE_ID);
    }

    @Test
    void unexpectedEventDoesNotRunWorkflow() {
        ConsumedMessageStore inbox = mock(ConsumedMessageStore.class);
        RedTeamStageProcessor processor = mock(RedTeamStageProcessor.class);
        RedTeamStageConsumer consumer = new RedTeamStageConsumer(inbox, processor, new ObjectMapper());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.consume(message("simulation.started")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(processor, never()).process(any(), any(), any());
    }

    private Message message(String eventType) {
        return MessageBuilder.withBody(("{\"roundId\":\"" + ROUND_ID + "\"}")
                        .getBytes(StandardCharsets.UTF_8))
                .setMessageId(MESSAGE_ID.toString())
                .setHeader("eventType", eventType)
                .setHeader("simulationId", SIMULATION_ID.toString())
                .build();
    }
}
