package com.cybersim.targetsystemservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class DemoTargetController {
    private final Map<String, Boolean> patched = new LinkedHashMap<>();

    public DemoTargetController() {
        patched.put("auth-required", false);
        patched.put("object-authorization", false);
        patched.put("rate-limit", false);
        patched.put("disable-debug-endpoint", false);
        patched.put("input-validation", false);
        patched.put("update-dependency-metadata", false);
    }

    @GetMapping("/demo/admin/report")
    public ResponseEntity<Map<String, Object>> adminReport() {
        if (patched.get("auth-required")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "auth-required"));
        }
        return ResponseEntity.ok(Map.of("status", "vulnerable", "issue", "missing authentication"));
    }

    @GetMapping("/demo/users/{id}/documents")
    public Map<String, Object> userDocuments(@PathVariable String id) {
        return Map.of("userId", id, "objectAuthorizationEnforced", patched.get("object-authorization"), "documents", List.of("demo-invoice.pdf"));
    }

    @PostMapping("/demo/login")
    public Map<String, Object> login() {
        return Map.of("rateLimitEnforced", patched.get("rate-limit"), "result", "mock-login-denied");
    }

    @GetMapping("/demo/debug/config")
    public ResponseEntity<Map<String, Object>> debugConfig() {
        if (patched.get("disable-debug-endpoint")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "disabled"));
        }
        return ResponseEntity.ok(Map.of("debug", true, "safeMockOnly", true));
    }

    @GetMapping("/demo/billing/search")
    public Map<String, Object> billingSearch(@RequestParam(defaultValue = "") String q) {
        return Map.of("query", q, "inputValidationEnforced", patched.get("input-validation"), "results", List.of());
    }

    @GetMapping("/demo/dependencies")
    public Map<String, Object> dependencies() {
        String version = patched.get("update-dependency-metadata") ? "simulated-safe-version" : "simulated-risk-version";
        return Map.of("dependencies", List.of(Map.of("name", "demo-web-lib", "version", version)));
    }

    @PostMapping("/internal/patches/{patchName}")
    public ResponseEntity<Map<String, Object>> applyPatch(@PathVariable String patchName, @RequestHeader(value = "X-Service-Token", required = false) String serviceToken) {
        if (serviceToken == null || serviceToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "service-to-service token required"));
        }
        if (!patched.containsKey(patchName)) {
            return ResponseEntity.notFound().build();
        }
        patched.put(patchName, true);
        return ResponseEntity.ok(Map.of("patch", patchName, "status", "APPLIED"));
    }

    @GetMapping("/internal/patches/status")
    public Map<String, Boolean> status() {
        return patched;
    }
}
