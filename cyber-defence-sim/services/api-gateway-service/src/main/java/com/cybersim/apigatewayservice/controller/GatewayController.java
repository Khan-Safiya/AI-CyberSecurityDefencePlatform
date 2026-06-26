package com.cybersim.apigatewayservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class GatewayController {
    @GetMapping("/")
    public Map<String, Object> index() {
        return Map.of("service", "api-gateway-service", "routes", List.of("/api/auth", "/api/targets", "/api/simulations", "/api/dashboard", "/api/integration"));
    }
}
