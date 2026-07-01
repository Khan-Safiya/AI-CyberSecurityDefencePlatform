package com.cybersim.remediationservice.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RemediationRabbitConfiguration {
    static final String EXCHANGE = "cybersim.events";
    static final String QUEUE = "cybersim.remediation.round-requests";
    static final String ROUTING_KEY = "simulation.round.blue-team.requested";
    static final String DEAD_EXCHANGE = "cybersim.dead-letter";
    static final String DEAD_QUEUE = "cybersim.remediation.dead-letter";
    static final String DEAD_KEY = "remediation";

    @Bean TopicExchange remediationEventsExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean Queue remediationQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(DEAD_KEY).build();
    }
    @Bean Binding remediationBinding(TopicExchange remediationEventsExchange, Queue remediationQueue) {
        return BindingBuilder.bind(remediationQueue).to(remediationEventsExchange).with(ROUTING_KEY);
    }
    @Bean DirectExchange remediationDeadExchange() { return new DirectExchange(DEAD_EXCHANGE, true, false); }
    @Bean Queue remediationDeadQueue() { return QueueBuilder.durable(DEAD_QUEUE).build(); }
    @Bean Binding remediationDeadBinding(Queue remediationDeadQueue, DirectExchange remediationDeadExchange) {
        return BindingBuilder.bind(remediationDeadQueue).to(remediationDeadExchange).with(DEAD_KEY);
    }
}
