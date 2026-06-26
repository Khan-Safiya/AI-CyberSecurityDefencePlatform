package com.cybersim.remediationservice.controller;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/remediations")
public class RemediationController {
    @PostMapping
    public Map<String, Object> create() {
        return Map.of("id", UUID.randomUUID(), "status", "PROPOSED", "createdAt", Instant.now());
    }

    @PostMapping("/{id}/apply")
    public Map<String, Object> apply(@PathVariable UUID id) {
        return Map.of("id", id, "status", "APPLIED", "patchSummary", "Applied safe sandbox patch");
    }

    @GetMapping("/simulations/{simulationId}")
    public List<Map<String, Object>> list(@PathVariable UUID simulationId) {
        return List.of(Map.of("simulationId", simulationId, "status", "VERIFIED", "count", 6));
    }
}
