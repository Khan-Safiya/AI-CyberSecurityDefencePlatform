package com.cybersim.dashboardbackendservice.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/dashboard/simulations/{simulationId}")
public class DashboardController {
    @GetMapping("/overview")
    public Map<String, Object> overview(@PathVariable UUID simulationId) {
        return Map.of(
                "simulationId", simulationId,
                "status", "COMPLETED",
                "rounds", 1,
                "openVulnerabilities", 0,
                "verifiedFixes", 6,
                "redTeamScore", 220,
                "blueTeamScore", 270,
                "finalRiskScore", 0
        );
    }

    @GetMapping("/timeline")
    public List<String> timeline() {
        return List.of("simulation.started", "vulnerability.discovered", "detection.created", "remediation.applied", "verification.completed", "simulation.completed");
    }
}
