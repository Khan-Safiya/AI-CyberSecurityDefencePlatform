package com.cybersim.eventlogservice.controller;

import com.cybersim.eventlogservice.store.EventStore;
import com.cybersim.shared.events.PlatformEvent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class EventLogController {
    private final EventStore eventStore;

    public EventLogController(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> append(@Valid @RequestBody PlatformEvent event) {
        UUID eventId = event.eventId() == null ? UUID.randomUUID() : event.eventId();
        PlatformEvent stored = eventStore.append(new PlatformEvent(
                eventId, event.eventType(), event.simulationId(), event.roundId(), event.targetId(),
                event.producerService(), event.correlationId(), event.timestamp(), event.payload()
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("eventId", stored.eventId(), "status", "STORED", "timestamp", Instant.now()));
    }

    @GetMapping({"/simulations/{simulationId}/events", "/simulations/{simulationId}/timeline"})
    public List<PlatformEvent> listBySimulation(@PathVariable UUID simulationId) {
        return eventStore.findBySimulationId(simulationId);
    }

    @GetMapping("/audit-logs")
    public List<PlatformEvent> auditLogs() {
        return eventStore.findAll();
    }
}
