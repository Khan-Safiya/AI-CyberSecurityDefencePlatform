package com.cybersim.blueteamagentservice.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/blue-team")
public class BlueTeamController {
    @PostMapping("/plan")
    public Map<String, Object> plan() {
        return Map.of("actions", List.of("TRIAGE_FINDING", "RECOMMEND_REMEDIATION", "APPLY_REMEDIATION", "VERIFY_REMEDIATION"));
    }
}
