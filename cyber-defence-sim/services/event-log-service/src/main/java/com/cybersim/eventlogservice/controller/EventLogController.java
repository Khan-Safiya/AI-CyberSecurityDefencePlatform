package com.cybersim.eventlogservice.controller;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class EventLogController {
    @PostMapping("/events")
    public Map<String, Object> append(@RequestBody(required = false) Map<String, Object> event) {
        return Map.of("eventId", UUID.randomUUID(), "status", "STORED", "timestamp", Instant.now());
    }

    @GetMapping({"/simulations/{simulationId}/events", "/simulations/{simulationId}/timeline", "/audit-logs"})
    public List<Map<String, Object>> list(@PathVariable(required = false) UUID simulationId) {
        return List.of(Map.of("eventType", "simulation.completed", "simulationId", simulationId == null ? "audit" : simulationId.toString(), "timestamp", Instant.now()));
    }
}
