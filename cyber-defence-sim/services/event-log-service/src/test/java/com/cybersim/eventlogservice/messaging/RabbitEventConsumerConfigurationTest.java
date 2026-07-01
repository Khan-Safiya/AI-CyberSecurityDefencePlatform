package com.cybersim.eventlogservice.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitEventConsumerConfigurationTest {
    private final RabbitEventConsumerConfiguration configuration = new RabbitEventConsumerConfiguration();

    @Test
    void declaresDurableSimulationQueueWithDeadLetterRouting() {
        Queue queue = configuration.eventLogQueue();

        assertThat(queue.getName()).isEqualTo(RabbitEventConsumerConfiguration.QUEUE_NAME);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange",
                        RabbitEventConsumerConfiguration.DEAD_LETTER_EXCHANGE_NAME)
                .containsEntry("x-dead-letter-routing-key",
                        RabbitEventConsumerConfiguration.DEAD_LETTER_ROUTING_KEY);
    }

    @Test
    void bindsOnlySimulationEventsAndRoutesFailuresToDurableQueue() {
        TopicExchange eventsExchange = configuration.platformEventsExchange();
        Queue eventQueue = configuration.eventLogQueue();
        Binding eventBinding = configuration.eventLogBinding(eventsExchange, eventQueue);
        DirectExchange deadLetterExchange = configuration.deadLetterExchange();
        Queue deadLetterQueue = configuration.eventLogDeadLetterQueue();
        Binding deadLetterBinding = configuration.eventLogDeadLetterBinding(deadLetterQueue, deadLetterExchange);

        assertThat(eventsExchange.isDurable()).isTrue();
        assertThat(eventBinding.getRoutingKey()).isEqualTo("simulation.#");
        assertThat(deadLetterExchange.isDurable()).isTrue();
        assertThat(deadLetterQueue.isDurable()).isTrue();
        assertThat(deadLetterBinding.getRoutingKey())
                .isEqualTo(RabbitEventConsumerConfiguration.DEAD_LETTER_ROUTING_KEY);
    }
}
