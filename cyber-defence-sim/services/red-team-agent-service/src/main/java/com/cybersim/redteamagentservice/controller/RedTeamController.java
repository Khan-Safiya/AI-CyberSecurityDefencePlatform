package com.cybersim.redteamagentservice.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/red-team")
public class RedTeamController {
    @PostMapping("/plan")
    public Map<String, Object> plan() {
        return Map.of("requiresPolicyApproval", true, "actions", List.of("SIMULATED_ENDPOINT_DISCOVERY", "SIMULATED_AUTH_REQUIRED_CHECK", "SIMULATED_ACCESS_CONTROL_CHECK"));
    }
}
