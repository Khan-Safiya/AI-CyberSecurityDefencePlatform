package com.cybersim.simulationorchestratorservice.outbox;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private final OutboxStore store;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxStore store, RabbitTemplate rabbitTemplate) {
        this.store = store;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:1000}")
    public void publishReady() {
        for (OutboxEventRecord event : store.findReady(Instant.now())) {
            publish(event);
        }
    }

    void publish(OutboxEventRecord event) {
        CorrelationData correlationData = new CorrelationData(event.id().toString());
        Message message = MessageBuilder.withBody(event.payloadJson().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(event.id().toString())
                .setHeader("eventType", event.eventType())
                .setHeader("simulationId", event.simulationId().toString())
                .build();
        try {
            rabbitTemplate.send(RabbitEventConfiguration.EXCHANGE_NAME, event.routingKey(), message, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("Broker did not acknowledge the event");
            }
            store.save(event.published(Instant.now()));
        } catch (Exception exception) {
            store.save(event.failed(Instant.now(), exception.getClass().getSimpleName()));
        }
    }
}
