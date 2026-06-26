package com.cybersim.detectionengineservice.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class DetectionController {
    @GetMapping("/detection-rules")
    public List<Map<String, Object>> rules() {
        return List.of(Map.of("name", "Sandbox red-team activity observed", "severity", "INFO", "enabled", true));
    }

    @GetMapping("/simulations/{id}/detections")
    public List<Map<String, String>> detections() {
        return List.of(Map.of("eventType", "detection.created", "message", "Safe simulated red-team action observed"));
    }
}
