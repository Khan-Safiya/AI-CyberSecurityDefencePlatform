package com.cybersim.simulationorchestratorservice.controller;

import com.cybersim.shared.observability.ApiErrors;
import com.cybersim.shared.dto.SimulationRequest;
import com.cybersim.shared.dto.SimulationResponse;
import com.cybersim.shared.dto.TargetMode;
import com.cybersim.simulationorchestratorservice.model.SimulationRecord;
import com.cybersim.simulationorchestratorservice.model.SimulationRoundRecord;
import com.cybersim.simulationorchestratorservice.outbox.OutboxEventFactory;
import com.cybersim.simulationorchestratorservice.outbox.OutboxStore;
import com.cybersim.simulationorchestratorservice.store.SimulationRoundStore;
import com.cybersim.simulationorchestratorservice.store.SimulationStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
public class SimulationController {
    private final SimulationStore simulationStore;
    private final SimulationRoundStore roundStore;
    private final OutboxStore outboxStore;
    private final OutboxEventFactory outboxEventFactory;

    public SimulationController(
            SimulationStore simulationStore,
            SimulationRoundStore roundStore,
            OutboxStore outboxStore,
            OutboxEventFactory outboxEventFactory
    ) {
        this.simulationStore = simulationStore;
        this.roundStore = roundStore;
        this.outboxStore = outboxStore;
        this.outboxEventFactory = outboxEventFactory;
    }

    @PostMapping({"/simulations", "/integration/targets/{targetId}/start-assessment"})
    @Transactional
    public ResponseEntity<SimulationResponse> start(@PathVariable(required = false) UUID targetId, @Valid @RequestBody(required = false) SimulationRequest request) {
        UUID simulationId = UUID.randomUUID();
        UUID resolvedTargetId = targetId != null ? targetId : request == null ? UUID.fromString("00000000-0000-0000-0000-000000000101") : request.targetId();
        String name = request == null || request.name() == null ? "Baseline Web Application Defence Simulation" : request.name();
        List<String> timeline = List.of("simulation.started", "round.1.created");
        Instant now = Instant.now();
        SimulationRecord simulation = new SimulationRecord(
                simulationId,
                name,
                request == null ? TargetMode.INTERNAL_SANDBOX : request.mode(),
                resolvedTargetId,
                "RUNNING",
                1,
                request == null ? 5 : request.maxRounds(),
                request == null ? 60 : request.maxDurationMinutes(),
                request == null ? 2 : request.stopWhenNoNewFindingsForRounds(),
                request == null ? "LOW" : request.minimumAcceptedRiskLevel(),
                request == null || request.retestEnabled(),
                request == null ? 10 : request.retestDelaySeconds(),
                0,
                0,
                50,
                null,
                timeline,
                now,
                null
        );
        SimulationResponse response = simulationStore.save(simulation).toResponse();
        SimulationRoundRecord firstRound = roundStore.save(SimulationRoundRecord.create(simulationId, 1, now));
        outboxStore.save(outboxEventFactory.create("simulation.started", simulationId, null,
                Map.of("targetId", resolvedTargetId.toString(), "mode", simulation.mode().name())));
        outboxStore.save(outboxEventFactory.create("simulation.round.created", simulationId, firstRound.id(),
                Map.of("roundNumber", 1, "status", firstRound.status())));
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping({"/simulations/{id}", "/integration/assessments/{id}"})
    public ResponseEntity<Object> get(@PathVariable UUID id) {
        return simulationStore.findById(id)
                .<ResponseEntity<Object>>map(simulation -> ResponseEntity.ok(simulation.toResponse()))
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, "Simulation not found: " + id, "/simulations/" + id));
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

    private ResponseEntity<Object> error(HttpStatus status, String message, String path) {
        return ApiErrors.response(status, message, path);
    }
}
