package com.cybersim.notificationservice.controller;

import com.cybersim.notificationservice.webhook.WebhookDestinationValidator;
import com.cybersim.shared.dto.WebhookDeliveryRequest;
import com.cybersim.shared.observability.ApiErrors;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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

    @PostMapping("/webhooks/deliveries")
    public ResponseEntity<Object> createDelivery(@Valid @RequestBody WebhookDeliveryRequest request) {
        var violation = WebhookDestinationValidator.findViolation(request.destinationUrl());
        if (violation.isPresent()) {
            return ApiErrors.response(HttpStatus.BAD_REQUEST,
                    "Unsafe webhook destination: " + violation.get(), "/webhooks/deliveries");
        }
        String host = URI.create(request.destinationUrl()).getHost();
        return ResponseEntity.accepted().body(Map.of(
                "event", request.event(),
                "destinationHost", host,
                "status", "QUEUED"
        ));
    }
}
