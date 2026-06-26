package com.cybersim.reportingservice.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ReportingController {
    @PostMapping("/reports/simulations/{simulationId}/generate")
    public Map<String, Object> generate(@PathVariable UUID simulationId) {
        return Map.of("reportId", UUID.randomUUID(), "simulationId", simulationId, "status", "GENERATED");
    }

    @GetMapping("/reports/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return Map.of("id", id, "sections", List.of("Executive summary", "Target details", "Vulnerabilities", "Remediation", "Timeline", "Recommendations"));
    }
}
