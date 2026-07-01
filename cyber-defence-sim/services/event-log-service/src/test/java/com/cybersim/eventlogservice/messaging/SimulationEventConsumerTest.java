package com.cybersim.eventlogservice.messaging;

import com.cybersim.eventlogservice.store.ConsumedMessageStore;
import com.cybersim.eventlogservice.store.EventStore;
import com.cybersim.shared.events.PlatformEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SimulationEventConsumerTest {
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Test
    void duplicateDeliveryDoesNotAppendTheEventTwice() {
        EventStore eventStore = mock(EventStore.class);
        InMemoryConsumedMessageStore inbox = new InMemoryConsumedMessageStore();
        SimulationEventConsumer consumer = new SimulationEventConsumer(eventStore, inbox, new ObjectMapper());
        Message message = validMessage();

        consumer.consume(message);
        consumer.consume(message);

        verify(eventStore, times(1)).append(any(PlatformEvent.class));
        assertThat(inbox.messageIds).containsExactly(MESSAGE_ID);
    }

    @Test
    void malformedMessageIsRejectedBeforeAnythingIsStored() {
        EventStore eventStore = mock(EventStore.class);
        ConsumedMessageStore inbox = mock(ConsumedMessageStore.class);
        Message message = MessageBuilder.withBody("not-json".getBytes(StandardCharsets.UTF_8))
                .setMessageId(MESSAGE_ID.toString())
                .setHeader("eventType", "simulation.started")
                .setHeader("simulationId", SIMULATION_ID.toString())
                .build();

        assertThatThrownBy(() -> new SimulationEventConsumer(eventStore, inbox, new ObjectMapper()).consume(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event payload is not valid JSON");

        verify(eventStore, never()).append(any());
        verify(inbox, never()).record(any(), any(), any());
    }

    private Message validMessage() {
        return MessageBuilder.withBody(("{\"simulationId\":\"" + SIMULATION_ID + "\"}")
                        .getBytes(StandardCharsets.UTF_8))
                .setMessageId(MESSAGE_ID.toString())
                .setHeader("eventType", "simulation.started")
                .setHeader("simulationId", SIMULATION_ID.toString())
                .build();
    }

    private static final class InMemoryConsumedMessageStore implements ConsumedMessageStore {
        private final Set<UUID> messageIds = new HashSet<>();

        @Override
        public boolean contains(UUID messageId) {
            return messageIds.contains(messageId);
        }

        @Override
        public void record(UUID messageId, String eventType, Instant consumedAt) {
            messageIds.add(messageId);
        }
    }
}
