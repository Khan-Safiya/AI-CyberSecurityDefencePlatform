package com.cybersim.redteamagentservice.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RedTeamRabbitConfiguration {
    static final String EXCHANGE = "cybersim.events";
    static final String QUEUE = "cybersim.red-team.round-requests";
    static final String ROUTING_KEY = "simulation.round.red-team.requested";
    static final String DEAD_EXCHANGE = "cybersim.dead-letter";
    static final String DEAD_QUEUE = "cybersim.red-team.dead-letter";
    static final String DEAD_KEY = "red-team";

    @Bean TopicExchange eventsExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean Queue redTeamQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(DEAD_KEY).build();
    }
    @Bean Binding redTeamBinding(TopicExchange eventsExchange, Queue redTeamQueue) {
        return BindingBuilder.bind(redTeamQueue).to(eventsExchange).with(ROUTING_KEY);
    }
    @Bean DirectExchange redTeamDeadExchange() { return new DirectExchange(DEAD_EXCHANGE, true, false); }
    @Bean Queue redTeamDeadQueue() { return QueueBuilder.durable(DEAD_QUEUE).build(); }
    @Bean Binding redTeamDeadBinding(Queue redTeamDeadQueue, DirectExchange redTeamDeadExchange) {
        return BindingBuilder.bind(redTeamDeadQueue).to(redTeamDeadExchange).with(DEAD_KEY);
    }
}
