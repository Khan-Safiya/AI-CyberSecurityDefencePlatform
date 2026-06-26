package com.cybersim.agentorchestratorservice.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agents")
public class AgentController {
    @GetMapping
    public List<Map<String, String>> agents() {
        return List.of(
                Map.of("team", "RED", "name", "ReconAgent", "status", "COMPLETED"),
                Map.of("team", "BLUE", "name", "PatchAgent", "status", "COMPLETED")
        );
    }
}
