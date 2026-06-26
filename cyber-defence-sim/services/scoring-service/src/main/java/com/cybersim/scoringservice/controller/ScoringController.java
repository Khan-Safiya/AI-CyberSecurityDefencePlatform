package com.cybersim.scoringservice.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ScoringController {
    @GetMapping("/simulations/{simulationId}/scores")
    public Map<String, Object> scores(@PathVariable UUID simulationId) {
        return Map.of("simulationId", simulationId, "redTeamScore", 220, "blueTeamScore", 270, "finalRiskScore", 0);
    }

    @GetMapping("/simulations/{simulationId}/score-events")
    public List<Map<String, Object>> scoreEvents(@PathVariable UUID simulationId) {
        return List.of(Map.of("simulationId", simulationId, "team", "RED", "points", 220), Map.of("simulationId", simulationId, "team", "BLUE", "points", 270));
    }
}
