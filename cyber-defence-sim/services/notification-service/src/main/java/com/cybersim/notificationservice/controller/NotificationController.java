package com.cybersim.notificationservice.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class NotificationController {
    @PostMapping("/internal/notifications")
    public Map<String, String> notifyInternal() {
        return Map.of("status", "ACCEPTED");
    }

    @GetMapping("/webhooks/deliveries")
    public List<Map<String, String>> deliveries() {
        return List.of(Map.of("event", "assessment.completed", "status", "DELIVERED"));
    }
}
