package com.cybersim.agentorchestratorservice.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitAgentEventConsumerConfiguration {
    static final String EXCHANGE_NAME = "cybersim.events";
    static final String QUEUE_NAME = "cybersim.agent-orchestrator.simulation-events";
    static final String DEAD_LETTER_EXCHANGE_NAME = "cybersim.dead-letter";
    static final String DEAD_LETTER_QUEUE_NAME = "cybersim.agent-orchestrator.dead-letter";
    static final String DEAD_LETTER_ROUTING_KEY = "agent-orchestrator";

    @Bean
    TopicExchange agentOrchestratorPlatformEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    Queue agentOrchestratorQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding agentOrchestratorBinding(TopicExchange agentOrchestratorPlatformEventsExchange, Queue agentOrchestratorQueue) {
        return BindingBuilder.bind(agentOrchestratorQueue).to(agentOrchestratorPlatformEventsExchange).with("simulation.#");
    }

    @Bean
    DirectExchange agentOrchestratorDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE_NAME, true, false);
    }

    @Bean
    Queue agentOrchestratorDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build();
    }

    @Bean
    Binding agentOrchestratorDeadLetterBinding(Queue agentOrchestratorDeadLetterQueue, DirectExchange agentOrchestratorDeadLetterExchange) {
        return BindingBuilder.bind(agentOrchestratorDeadLetterQueue)
                .to(agentOrchestratorDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}
