package com.cybersim.eventlogservice.store;

import com.cybersim.shared.events.PlatformEvent;

import java.util.List;
import java.util.UUID;

public interface EventStore {
    PlatformEvent append(PlatformEvent event);

    List<PlatformEvent> findBySimulationId(UUID simulationId);

    List<PlatformEvent> findAll();
}
