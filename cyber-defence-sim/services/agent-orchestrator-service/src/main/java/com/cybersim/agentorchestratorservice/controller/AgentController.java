package com.cybersim.agentorchestratorservice.controller;

import com.cybersim.agentorchestratorservice.store.AgentStatusStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/agents")
public class AgentController {
    private final AgentStatusStore agentStatusStore;

    public AgentController(AgentStatusStore agentStatusStore) {
        this.agentStatusStore = agentStatusStore;
    }

    @GetMapping("/{simulationId}")
    public List<Map<String, String>> agents(@PathVariable UUID simulationId) {
        return agentStatusStore.findBySimulationId(simulationId).stream()
                .map(record -> Map.of(
                        "team", record.team(),
                        "name", record.agentName(),
                        "status", record.status(),
                        "updatedAt", record.updatedAt().toString()))
                .toList();
    }
}
