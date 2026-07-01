package com.cybersim.eventlogservice.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

interface EventReadRepository extends Repository<EventEntity, UUID> {
    boolean existsById(UUID id);

    List<EventEntity> findAllBySimulationIdOrderByEventTimestampAscEventIdAsc(UUID simulationId);

    List<EventEntity> findAllByOrderByEventTimestampAscEventIdAsc();
}
