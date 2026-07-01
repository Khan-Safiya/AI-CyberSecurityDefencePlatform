package com.cybersim.eventlogservice.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitEventConsumerConfiguration {
    static final String EXCHANGE_NAME = "cybersim.events";
    static final String QUEUE_NAME = "cybersim.event-log.simulation-events";
    static final String DEAD_LETTER_EXCHANGE_NAME = "cybersim.dead-letter";
    static final String DEAD_LETTER_QUEUE_NAME = "cybersim.event-log.dead-letter";
    static final String DEAD_LETTER_ROUTING_KEY = "event-log";

    @Bean
    TopicExchange platformEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    Queue eventLogQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding eventLogBinding(TopicExchange platformEventsExchange, Queue eventLogQueue) {
        return BindingBuilder.bind(eventLogQueue).to(platformEventsExchange).with("simulation.#");
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE_NAME, true, false);
    }

    @Bean
    Queue eventLogDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build();
    }

    @Bean
    Binding eventLogDeadLetterBinding(Queue eventLogDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(eventLogDeadLetterQueue)
                .to(deadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}
