package com.cybersim.blueteamagentservice.controller;

import com.cybersim.blueteamagentservice.client.RemediationClient;
import com.cybersim.shared.dto.RemediationResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/blue-team")
public class BlueTeamController {
    private final RemediationClient remediationClient;

    public BlueTeamController(RemediationClient remediationClient) {
        this.remediationClient = remediationClient;
    }

    @PostMapping("/plan")
    public Map<String, Object> plan() {
        return Map.of("actions", List.of("TRIAGE_FINDING", "RECOMMEND_REMEDIATION", "APPLY_REMEDIATION", "VERIFY_REMEDIATION"));
    }

    @GetMapping("/plan/{simulationId}")
    public List<RemediationResponse> plan(@PathVariable UUID simulationId) {
        return remediationClient.forSimulation(simulationId);
    }

    @GetMapping("/plan/{simulationId}/{roundId}")
    public List<RemediationResponse> plan(@PathVariable UUID simulationId, @PathVariable UUID roundId) {
        return remediationClient.forSimulation(simulationId).stream()
                .filter(remediation -> roundId.equals(remediation.roundId()))
                .toList();
    }
}
