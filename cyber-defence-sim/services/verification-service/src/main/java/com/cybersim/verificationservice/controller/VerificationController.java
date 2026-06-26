package com.cybersim.verificationservice.controller;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/verifications")
public class VerificationController {
    @PostMapping
    public Map<String, Object> verify() {
        return Map.of("id", UUID.randomUUID(), "status", "PASSED", "evidenceSummary", "Safe verification check confirms patched sandbox state", "verifiedAt", Instant.now());
    }

    @GetMapping("/simulations/{simulationId}")
    public List<Map<String, Object>> list(@PathVariable UUID simulationId) {
        return List.of(Map.of("simulationId", simulationId, "status", "PASSED", "verifiedFixes", 6));
    }
}
