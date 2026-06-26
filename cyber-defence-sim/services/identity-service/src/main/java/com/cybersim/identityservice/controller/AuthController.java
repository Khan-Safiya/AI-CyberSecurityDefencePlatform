package com.cybersim.identityservice.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @PostMapping("/login")
    public Map<String, Object> login() {
        return Map.of("tokenType", "Bearer", "accessToken", "demo-jwt-token", "roles", List.of("ADMIN", "SIMULATION_OPERATOR", "AUDITOR"));
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return Map.of("id", UUID.fromString("00000000-0000-0000-0000-000000000001"), "username", "demo-admin", "roles", List.of("ADMIN"));
    }
}
