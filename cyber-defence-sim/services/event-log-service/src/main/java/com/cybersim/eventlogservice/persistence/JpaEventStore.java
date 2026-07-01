package com.cybersim.eventlogservice.persistence;

import com.cybersim.eventlogservice.store.EventStore;
import com.cybersim.shared.events.PlatformEvent;
import com.cybersim.shared.exceptions.ConflictException;
import com.cybersim.shared.exceptions.PlatformException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@Transactional
class JpaEventStore implements EventStore {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final EntityManager entityManager;
    private final EventReadRepository readRepository;
    private final ObjectMapper objectMapper;

    JpaEventStore(EntityManager entityManager, EventReadRepository readRepository, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.readRepository = readRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public PlatformEvent append(PlatformEvent event) {
        if (readRepository.existsById(event.eventId())) {
            throw new ConflictException("Event already exists: " + event.eventId());
        }
        EventEntity entity = EventEntity.from(event, serialize(event.payload()), Instant.now());
        entityManager.persist(entity);
        entityManager.flush();
        return event;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformEvent> findBySimulationId(UUID simulationId) {
        return readRepository.findAllBySimulationIdOrderByEventTimestampAscEventIdAsc(simulationId).stream()
                .map(this::toEvent)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformEvent> findAll() {
        return readRepository.findAllByOrderByEventTimestampAscEventIdAsc().stream()
                .map(this::toEvent)
                .toList();
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new PlatformException("Event payload could not be serialized");
        }
    }

    private PlatformEvent toEvent(EventEntity entity) {
        try {
            return new PlatformEvent(entity.eventId(), entity.eventType(), entity.simulationId(), entity.roundId(),
                    entity.targetId(), entity.producerService(), entity.correlationId(), entity.eventTimestamp(),
                    objectMapper.readValue(entity.payloadJson(), PAYLOAD_TYPE));
        } catch (JsonProcessingException exception) {
            throw new PlatformException("Stored event payload could not be read");
        }
    }
}
