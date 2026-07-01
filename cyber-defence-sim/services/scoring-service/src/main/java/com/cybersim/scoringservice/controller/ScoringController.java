package com.cybersim.scoringservice.controller;

import com.cybersim.scoringservice.model.ScoreEventRecord;
import com.cybersim.scoringservice.store.ScoreAppendResult;
import com.cybersim.scoringservice.store.ScoreEventStore;
import com.cybersim.shared.dto.ScoreEventCreateRequest;
import com.cybersim.shared.dto.ScoreEventResponse;
import com.cybersim.shared.dto.SimulationScoreResponse;
import com.cybersim.shared.observability.ApiErrors;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ScoringController {
    private final ScoreEventStore store;
    private final String expectedServiceToken;

    public ScoringController(ScoreEventStore store, @Value("${scoring.service-auth-token}") String expectedServiceToken) {
        this.store = store;
        this.expectedServiceToken = expectedServiceToken;
    }

    @GetMapping("/simulations/{id}/scores")
    public SimulationScoreResponse scores(@PathVariable UUID id) {
        List<ScoreEventRecord> events = store.findBySimulationId(id);
        long redScore = total(events, "RED");
        long blueScore = total(events, "BLUE");
        long calculatedRisk = 50L + redScore - blueScore;
        int finalRiskScore = (int) Math.max(0L, Math.min(100L, calculatedRisk));
        List<UUID> blockedAgentIds = events.stream().filter(ScoreEventRecord::agentBlocked)
                .map(ScoreEventRecord::agentId).distinct().toList();
        return new SimulationScoreResponse(id, redScore, blueScore, finalRiskScore, events.size(), blockedAgentIds);
    }

    @GetMapping("/simulations/{id}/score-events")
    public List<ScoreEventResponse> scoreEvents(@PathVariable UUID id) {
        return store.findBySimulationId(id).stream().map(ScoreEventRecord::toResponse).toList();
    }

    @PostMapping("/internal/score-events")
    public ResponseEntity<Object> append(
            @RequestHeader(value = "X-Service-Token", required = false) String serviceToken,
            @Valid @RequestBody ScoreEventCreateRequest request
    ) {
        if (!validToken(serviceToken)) {
            return ApiErrors.response(HttpStatus.UNAUTHORIZED, "Valid service-to-service token required",
                    "/internal/score-events");
        }
        ScoreAppendResult result;
        try {
            result = store.append(ScoreEventRecord.from(request));
        } catch (DataIntegrityViolationException exception) {
            result = store.findBySimulationIdAndSourceEventId(request.simulationId(), request.sourceEventId())
                    .map(event -> new ScoreAppendResult(event, false))
                    .orElseThrow(() -> exception);
        }
        if (!result.created() && !result.event().representsSameRequest(request)) {
            return ApiErrors.response(HttpStatus.CONFLICT,
                    "Source event has already been scored with different details", "/internal/score-events");
        }
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.event().toResponse());
    }

    private long total(List<ScoreEventRecord> events, String team) {
        return events.stream().filter(event -> team.equals(event.team())).mapToLong(ScoreEventRecord::points).sum();
    }

    private boolean validToken(String serviceToken) {
        return serviceToken != null && !serviceToken.isBlank() && serviceToken.equals(expectedServiceToken);
    }
}
