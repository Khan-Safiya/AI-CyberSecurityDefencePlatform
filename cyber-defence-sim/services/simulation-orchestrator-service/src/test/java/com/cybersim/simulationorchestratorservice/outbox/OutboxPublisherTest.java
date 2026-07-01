package com.cybersim.simulationorchestratorservice.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class OutboxPublisherTest {
    private InMemoryOutboxStore store;
    private RabbitTemplate rabbitTemplate;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        store = new InMemoryOutboxStore();
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new OutboxPublisher(store, rabbitTemplate);
    }

    @Test
    void marksEventPublishedOnlyAfterBrokerAcknowledgement() {
        OutboxEventRecord event = store.save(event());
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(), any(CorrelationData.class));

        publisher.publish(event);

        OutboxEventRecord stored = store.events.get(event.id());
        assertThat(stored.status()).isEqualTo("PUBLISHED");
        assertThat(stored.attempts()).isEqualTo(1);
        assertThat(stored.publishedAt()).isNotNull();
    }

    @Test
    void failedPublishSchedulesRetryWithoutLosingEvent() {
        OutboxEventRecord event = store.save(event());
        doThrow(new AmqpConnectException(new IllegalStateException("offline"))).when(rabbitTemplate)
                .send(anyString(), anyString(), any(), any(CorrelationData.class));

        publisher.publish(event);

        OutboxEventRecord stored = store.events.get(event.id());
        assertThat(stored.status()).isEqualTo("FAILED");
        assertThat(stored.attempts()).isEqualTo(1);
        assertThat(stored.nextAttemptAt()).isAfter(event.nextAttemptAt());
        assertThat(stored.lastError()).isEqualTo("AmqpConnectException");
    }

    private OutboxEventRecord event() {
        Instant now = Instant.now();
        return OutboxEventRecord.pending("simulation.started", "simulation.started", UUID.randomUUID(), null,
                "{\"status\":\"RUNNING\"}", now);
    }

    private static final class InMemoryOutboxStore implements OutboxStore {
        private final Map<UUID, OutboxEventRecord> events = new LinkedHashMap<>();
        public OutboxEventRecord save(OutboxEventRecord event) { events.put(event.id(), event); return event; }
        public List<OutboxEventRecord> findReady(Instant now) {
            return events.values().stream().filter(event -> !"PUBLISHED".equals(event.status()))
                    .filter(event -> !event.nextAttemptAt().isAfter(now)).toList();
        }
        public List<OutboxEventRecord> findBySimulationId(UUID simulationId) {
            return events.values().stream().filter(event -> simulationId.equals(event.simulationId())).toList();
        }
    }
}
