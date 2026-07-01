package com.cybersim.scoringservice.messaging;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
@Configuration
class ScoringRabbitConfiguration {
    static final String EXCHANGE="cybersim.events", QUEUE="cybersim.scoring.round-requests";
    static final String ROUTING_KEY="simulation.round.scoring.requested";
    static final String DEAD_EXCHANGE="cybersim.dead-letter", DEAD_QUEUE="cybersim.scoring.dead-letter", DEAD_KEY="scoring";
    @Bean TopicExchange scoringEventsExchange(){return new TopicExchange(EXCHANGE,true,false);}
    @Bean Queue scoringQueue(){return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(DEAD_KEY).build();}
    @Bean Binding scoringBinding(TopicExchange scoringEventsExchange,Queue scoringQueue){return BindingBuilder.bind(scoringQueue).to(scoringEventsExchange).with(ROUTING_KEY);}
    @Bean DirectExchange scoringDeadExchange(){return new DirectExchange(DEAD_EXCHANGE,true,false);}
    @Bean Queue scoringDeadQueue(){return QueueBuilder.durable(DEAD_QUEUE).build();}
    @Bean Binding scoringDeadBinding(Queue scoringDeadQueue,DirectExchange scoringDeadExchange){return BindingBuilder.bind(scoringDeadQueue).to(scoringDeadExchange).with(DEAD_KEY);}
}
