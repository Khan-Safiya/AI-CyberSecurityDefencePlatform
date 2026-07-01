package com.cybersim.simulationorchestratorservice.outbox;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitEventConfiguration {
    static final String EXCHANGE_NAME = "cybersim.events";

    @Bean
    TopicExchange platformEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }
}
