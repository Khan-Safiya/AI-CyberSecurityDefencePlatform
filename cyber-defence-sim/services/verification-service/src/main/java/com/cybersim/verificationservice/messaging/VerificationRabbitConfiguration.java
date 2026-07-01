package com.cybersim.verificationservice.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class VerificationRabbitConfiguration {
    static final String EXCHANGE = "cybersim.events";
    static final String QUEUE = "cybersim.verification.round-requests";
    static final String ROUTING_KEY = "simulation.round.verification.requested";
    static final String DEAD_EXCHANGE = "cybersim.dead-letter";
    static final String DEAD_QUEUE = "cybersim.verification.dead-letter";
    static final String DEAD_KEY = "verification";

    @Bean TopicExchange verificationEventsExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean Queue verificationQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(DEAD_KEY).build();
    }
    @Bean Binding verificationBinding(TopicExchange verificationEventsExchange, Queue verificationQueue) {
        return BindingBuilder.bind(verificationQueue).to(verificationEventsExchange).with(ROUTING_KEY);
    }
    @Bean DirectExchange verificationDeadExchange() { return new DirectExchange(DEAD_EXCHANGE, true, false); }
    @Bean Queue verificationDeadQueue() { return QueueBuilder.durable(DEAD_QUEUE).build(); }
    @Bean Binding verificationDeadBinding(Queue verificationDeadQueue, DirectExchange verificationDeadExchange) {
        return BindingBuilder.bind(verificationDeadQueue).to(verificationDeadExchange).with(DEAD_KEY);
    }
}
