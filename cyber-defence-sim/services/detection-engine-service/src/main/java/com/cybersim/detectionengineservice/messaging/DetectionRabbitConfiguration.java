package com.cybersim.detectionengineservice.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DetectionRabbitConfiguration {
    static final String EXCHANGE = "cybersim.events";
    static final String QUEUE = "cybersim.detection.round-requests";
    static final String ROUTING_KEY = "simulation.round.detection.requested";
    static final String DEAD_EXCHANGE = "cybersim.dead-letter";
    static final String DEAD_QUEUE = "cybersim.detection.dead-letter";
    static final String DEAD_KEY = "detection";

    @Bean TopicExchange detectionEventsExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean Queue detectionQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(DEAD_KEY).build();
    }
    @Bean Binding detectionBinding(TopicExchange detectionEventsExchange, Queue detectionQueue) {
        return BindingBuilder.bind(detectionQueue).to(detectionEventsExchange).with(ROUTING_KEY);
    }
    @Bean DirectExchange detectionDeadExchange() { return new DirectExchange(DEAD_EXCHANGE, true, false); }
    @Bean Queue detectionDeadQueue() { return QueueBuilder.durable(DEAD_QUEUE).build(); }
    @Bean Binding detectionDeadBinding(Queue detectionDeadQueue, DirectExchange detectionDeadExchange) {
        return BindingBuilder.bind(detectionDeadQueue).to(detectionDeadExchange).with(DEAD_KEY);
    }
}
