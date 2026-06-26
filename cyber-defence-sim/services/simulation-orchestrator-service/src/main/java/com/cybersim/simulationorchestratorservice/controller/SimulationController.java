package com.cybersim.simulationorchestratorservice.controller;

import com.cybersim.shared.dto.SimulationRequest;
import com.cybersim.shared.dto.SimulationResponse;
import com.cybersim.shared.dto.TargetMode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping
public class SimulationController {
    private final Map<UUID, SimulationResponse> simulations = new ConcurrentHashMap<>();

    @PostMapping({"/simulations", "/integration/targets/{targetId}/start-assessment"})
    public ResponseEntity<SimulationResponse> start(@PathVariable(required = false) UUID targetId, @RequestBody(required = false) SimulationRequest request) {
        UUID simulationId = UUID.randomUUID();
        UUID resolvedTargetId = targetId != null ? targetId : request == null ? UUID.fromString("00000000-0000-0000-0000-000000000101") : request.targetId();
        String name = request == null || request.name() == null ? "Baseline Web Application Defence Simulation" : request.name();
        List<String> timeline = new ArrayList<>();
        timeline.add("simulation.started");
        timeline.add("round.1.red-team.completed: 6 safe sandbox findings discovered");
        timeline.add("round.1.detection.created: 6 detections generated");
        timeline.add("round.1.blue-team.completed: remediation proposed and applied");
        timeline.add("round.1.verification.completed: fixes verified");
        timeline.add("simulation.completed");
        SimulationResponse response = new SimulationResponse(
                simulationId,
                name,
                request == null ? TargetMode.INTERNAL_SANDBOX : request.mode(),
                resolvedTargetId,
                "COMPLETED",
                1,
                220,
                270,
                0,
                timeline,
                Instant.now(),
                Instant.now()
        );
        simulations.put(simulationId, response);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping({"/simulations/{id}", "/integration/assessments/{id}"})
    public ResponseEntity<SimulationResponse> get(@PathVariable UUID id) {
        SimulationResponse response = simulations.get(id);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }

    @GetMapping("/scenarios/default")
    public Map<String, Object> defaultScenario() {
        return Map.of(
                "name", "Baseline Web Application Defence Simulation",
                "mode", "INTERNAL_SANDBOX",
                "maxRounds", 5,
                "stopWhenNoNewFindingsForRounds", 2,
                "vulnerabilities", List.of(
                        "Missing authentication on /demo/admin/report",
                        "Missing object-level authorization on /demo/users/{id}/documents",
                        "Missing rate limit on /demo/login",
                        "Exposed mock debug endpoint /demo/debug/config",
                        "Missing input validation on /demo/billing/search",
                        "Simulated outdated dependency metadata on /demo/dependencies"
                )
        );
    }
}
