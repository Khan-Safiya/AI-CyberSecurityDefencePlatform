package com.cybersim.simulationorchestratorservice.controller;

import com.cybersim.shared.dto.RoundCompletionRequest;
import com.cybersim.shared.dto.RoundCompletionResponse;
import com.cybersim.shared.dto.SimulationRoundResponse;
import com.cybersim.shared.observability.ApiErrors;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class RoundController {
    private final SimulationStore simulationStore;
    private final SimulationRoundStore roundStore;
    private final OutboxStore outboxStore;
    private final OutboxEventFactory outboxEventFactory;

    public RoundController(
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

    @GetMapping("/simulations/{simulationId}/rounds")
    public List<SimulationRoundResponse> rounds(@PathVariable UUID simulationId) {
        return roundStore.findBySimulationId(simulationId).stream().map(SimulationRoundRecord::toResponse).toList();
    }

    @PostMapping("/simulations/{simulationId}/rounds/{roundId}/advance")
    @Transactional
    public ResponseEntity<Object> advance(
            @PathVariable UUID simulationId,
            @PathVariable UUID roundId
    ) {
        SimulationRecord simulation = simulationStore.findById(simulationId).orElse(null);
        SimulationRoundRecord round = roundStore.findById(roundId).orElse(null);
        String path = roundPath(simulationId, roundId, "advance");
        if (simulation == null || round == null || !simulationId.equals(round.simulationId())) {
            return ApiErrors.response(HttpStatus.NOT_FOUND, "Simulation round not found", path);
        }
        if (!"RUNNING".equals(simulation.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Simulation is not running", path);
        }
        try {
            SimulationRoundRecord advanced = roundStore.save(round.advance());
            String eventType = switch (advanced.status()) {
                case "RED_TEAM_RUNNING" -> "simulation.round.red-team.requested";
                case "DETECTION_RUNNING" -> "simulation.round.detection.requested";
                case "BLUE_TEAM_RUNNING" -> "simulation.round.blue-team.requested";
                case "VERIFYING" -> "simulation.round.verification.requested";
                case "SCORING" -> "simulation.round.scoring.requested";
                default -> "simulation.round.advanced";
            };
            outboxStore.save(outboxEventFactory.create(eventType, simulationId, roundId,
                    Map.of("roundNumber", advanced.roundNumber(), "status", advanced.status())));
            return ResponseEntity.ok(advanced.toResponse());
        } catch (IllegalStateException exception) {
            return ApiErrors.response(HttpStatus.CONFLICT, exception.getMessage(), path);
        }
    }

    @PostMapping("/simulations/{simulationId}/rounds/{roundId}/red-team-complete")
    @Transactional
    public ResponseEntity<Object> redTeamComplete(
            @PathVariable UUID simulationId,
            @PathVariable UUID roundId
    ) {
        String path = roundPath(simulationId, roundId, "red-team-complete");
        SimulationRecord simulation = simulationStore.findById(simulationId).orElse(null);
        SimulationRoundRecord round = roundStore.findById(roundId).orElse(null);
        if (simulation == null || round == null || !simulationId.equals(round.simulationId())) {
            return ApiErrors.response(HttpStatus.NOT_FOUND, "Simulation round not found", path);
        }
        if (!"RUNNING".equals(simulation.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Simulation is not running", path);
        }
        if ("CREATED".equals(round.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Red-team stage has not started", path);
        }
        if (!"RED_TEAM_RUNNING".equals(round.status())) {
            return ResponseEntity.ok(round.toResponse());
        }

        SimulationRoundRecord advanced = roundStore.save(round.advance());
        outboxStore.save(outboxEventFactory.create("simulation.round.detection.requested",
                simulationId, roundId,
                Map.of("roundNumber", advanced.roundNumber(), "status", advanced.status())));
        return ResponseEntity.ok(advanced.toResponse());
    }

    @PostMapping("/simulations/{simulationId}/rounds/{roundId}/detection-complete")
    @Transactional
    public ResponseEntity<Object> detectionComplete(
            @PathVariable UUID simulationId,
            @PathVariable UUID roundId
    ) {
        String path = roundPath(simulationId, roundId, "detection-complete");
        SimulationRecord simulation = simulationStore.findById(simulationId).orElse(null);
        SimulationRoundRecord round = roundStore.findById(roundId).orElse(null);
        if (simulation == null || round == null || !simulationId.equals(round.simulationId())) {
            return ApiErrors.response(HttpStatus.NOT_FOUND, "Simulation round not found", path);
        }
        if (!"RUNNING".equals(simulation.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Simulation is not running", path);
        }
        if ("CREATED".equals(round.status()) || "RED_TEAM_RUNNING".equals(round.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Detection stage has not started", path);
        }
        if (!"DETECTION_RUNNING".equals(round.status())) {
            return ResponseEntity.ok(round.toResponse());
        }

        SimulationRoundRecord advanced = roundStore.save(round.advance());
        outboxStore.save(outboxEventFactory.create("simulation.round.blue-team.requested",
                simulationId, roundId,
                Map.of("roundNumber", advanced.roundNumber(), "status", advanced.status())));
        return ResponseEntity.ok(advanced.toResponse());
    }

    @PostMapping("/simulations/{simulationId}/rounds/{roundId}/blue-team-complete")
    @Transactional
    public ResponseEntity<Object> blueTeamComplete(
            @PathVariable UUID simulationId,
            @PathVariable UUID roundId
    ) {
        String path = roundPath(simulationId, roundId, "blue-team-complete");
        SimulationRecord simulation = simulationStore.findById(simulationId).orElse(null);
        SimulationRoundRecord round = roundStore.findById(roundId).orElse(null);
        if (simulation == null || round == null || !simulationId.equals(round.simulationId())) {
            return ApiErrors.response(HttpStatus.NOT_FOUND, "Simulation round not found", path);
        }
        if (!"RUNNING".equals(simulation.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Simulation is not running", path);
        }
        if (List.of("CREATED", "RED_TEAM_RUNNING", "DETECTION_RUNNING").contains(round.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Blue-team stage has not started", path);
        }
        if (!"BLUE_TEAM_RUNNING".equals(round.status())) {
            return ResponseEntity.ok(round.toResponse());
        }

        SimulationRoundRecord advanced = roundStore.save(round.advance());
        outboxStore.save(outboxEventFactory.create("simulation.round.verification.requested",
                simulationId, roundId,
                Map.of("roundNumber", advanced.roundNumber(), "status", advanced.status())));
        return ResponseEntity.ok(advanced.toResponse());
    }

    @PostMapping("/simulations/{simulationId}/rounds/{roundId}/verification-complete")
    @Transactional
    public ResponseEntity<Object> verificationComplete(
            @PathVariable UUID simulationId,
            @PathVariable UUID roundId
    ) {
        String path = roundPath(simulationId, roundId, "verification-complete");
        SimulationRecord simulation = simulationStore.findById(simulationId).orElse(null);
        SimulationRoundRecord round = roundStore.findById(roundId).orElse(null);
        if (simulation == null || round == null || !simulationId.equals(round.simulationId())) {
            return ApiErrors.response(HttpStatus.NOT_FOUND, "Simulation round not found", path);
        }
        if (!"RUNNING".equals(simulation.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Simulation is not running", path);
        }
        if (!"VERIFYING".equals(round.status()) && !"SCORING".equals(round.status())
                && !"COMPLETED".equals(round.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Verification stage has not started", path);
        }
        if (!"VERIFYING".equals(round.status())) {
            return ResponseEntity.ok(round.toResponse());
        }

        SimulationRoundRecord advanced = roundStore.save(round.advance());
        outboxStore.save(outboxEventFactory.create("simulation.round.scoring.requested",
                simulationId, roundId,
                Map.of("roundNumber", advanced.roundNumber(), "status", advanced.status())));
        return ResponseEntity.ok(advanced.toResponse());
    }

    @PostMapping("/simulations/{simulationId}/rounds/{roundId}/complete")
    @Transactional
    public ResponseEntity<Object> complete(
            @PathVariable UUID simulationId,
            @PathVariable UUID roundId,
            @Valid @RequestBody RoundCompletionRequest request
    ) {
        SimulationRecord simulation = simulationStore.findById(simulationId).orElse(null);
        SimulationRoundRecord round = roundStore.findById(roundId).orElse(null);
        String path = roundPath(simulationId, roundId, "complete");
        if (simulation == null || round == null || !simulationId.equals(round.simulationId())) {
            return ApiErrors.response(HttpStatus.NOT_FOUND, "Simulation round not found", path);
        }
        if (!"RUNNING".equals(simulation.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Simulation is not running", path);
        }

        Instant now = Instant.now();
        SimulationRoundRecord completed;
        try {
            completed = roundStore.save(round.complete(request, now));
        } catch (IllegalStateException exception) {
            return ApiErrors.response(HttpStatus.CONFLICT, exception.getMessage(), path);
        }

        StopDecision decision = stopDecision(simulation, completed, request, now);
        SimulationRoundRecord nextRound = null;
        int nextRoundNumber = completed.roundNumber() + 1;
        if (decision.status() == null) {
            nextRound = roundStore.save(SimulationRoundRecord.create(simulationId, nextRoundNumber, now));
        }
        SimulationRecord updated = simulation.afterRound(completed.roundNumber(), nextRoundNumber,
                request.redTeamScore(), request.blueTeamScore(), request.riskScoreAfter(), decision.status(),
                decision.reason(), now);
        simulationStore.save(updated);
        outboxStore.save(outboxEventFactory.create("simulation.round.completed", simulationId, completed.id(),
                Map.of(
                        "roundNumber", completed.roundNumber(),
                        "newFindingsCount", completed.newFindingsCount(),
                        "verifiedFixesCount", completed.verifiedFixesCount(),
                        "riskScoreAfter", completed.riskScoreAfter()
                )));
        if (nextRound != null) {
            outboxStore.save(outboxEventFactory.create("simulation.round.created", simulationId, nextRound.id(),
                    Map.of("roundNumber", nextRound.roundNumber(), "status", nextRound.status())));
        } else {
            outboxStore.save(outboxEventFactory.create("simulation." + updated.status().toLowerCase(),
                    simulationId, completed.id(), Map.of("stopReason", decision.reason())));
        }
        return ResponseEntity.ok(new RoundCompletionResponse(updated.toResponse(), completed.toResponse(),
                nextRound == null ? null : nextRound.toResponse(), decision.reason()));
    }

    @PostMapping("/simulations/{simulationId}/stop")
    @Transactional
    public ResponseEntity<Object> stop(@PathVariable UUID simulationId) {
        String path = "/simulations/" + simulationId + "/stop";
        SimulationRecord simulation = simulationStore.findById(simulationId).orElse(null);
        if (simulation == null) {
            return ApiErrors.response(HttpStatus.NOT_FOUND, "Simulation not found", path);
        }
        if (!"RUNNING".equals(simulation.status()) && !"PAUSED".equals(simulation.status())) {
            return ApiErrors.response(HttpStatus.CONFLICT, "Only an active simulation can be stopped", path);
        }
        Instant now = Instant.now();
        roundStore.findBySimulationId(simulationId).stream()
                .filter(round -> round.endedAt() == null)
                .findFirst()
                .ifPresent(round -> roundStore.save(round.fail(now)));
        SimulationRecord stopped = simulationStore.save(simulation.manuallyStopped(now));
        outboxStore.save(outboxEventFactory.create("simulation.cancelled", simulationId, null,
                Map.of("stopReason", "MANUAL_STOP")));
        return ResponseEntity.ok(stopped.toResponse());
    }

    private StopDecision stopDecision(
            SimulationRecord simulation,
            SimulationRoundRecord completed,
            RoundCompletionRequest request,
            Instant now
    ) {
        if (request.unsafeActionDetected()) {
            return new StopDecision("FAILED", "UNSAFE_ACTION_DETECTED");
        }
        if (request.policyViolationDetected()) {
            return new StopDecision("FAILED", "POLICY_VIOLATION");
        }
        if (!request.targetAvailable()) {
            return new StopDecision("FAILED", "TARGET_UNAVAILABLE");
        }
        if (request.allCriticalAndHighFixed()) {
            return new StopDecision("COMPLETED", "ALL_HIGH_CRITICAL_FIXED");
        }
        if (!simulation.retestEnabled()) {
            return new StopDecision("COMPLETED", "RETEST_DISABLED");
        }
        if (Duration.between(simulation.startedAt(), now).toMinutes() >= simulation.maxDurationMinutes()) {
            return new StopDecision("COMPLETED", "MAX_DURATION_REACHED");
        }
        if (completed.roundNumber() >= simulation.maxRounds()) {
            return new StopDecision("COMPLETED", "MAX_ROUNDS_REACHED");
        }
        List<SimulationRoundRecord> completedRounds = roundStore.findBySimulationId(simulation.id()).stream()
                .filter(round -> "COMPLETED".equals(round.status())).toList();
        int threshold = simulation.stopWhenNoNewFindingsForRounds();
        if (completedRounds.size() >= threshold
                && completedRounds.subList(completedRounds.size() - threshold, completedRounds.size()).stream()
                .allMatch(round -> round.newFindingsCount() == 0)) {
            return new StopDecision("COMPLETED", "NO_NEW_FINDINGS_THRESHOLD_REACHED");
        }
        return new StopDecision(null, null);
    }

    private String roundPath(UUID simulationId, UUID roundId, String action) {
        return "/simulations/" + simulationId + "/rounds/" + roundId + "/" + action;
    }


    private record StopDecision(String status, String reason) {
    }
}
