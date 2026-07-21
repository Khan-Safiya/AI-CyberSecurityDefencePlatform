package com.cybersim.targetsystemservice.controller;

import com.cybersim.shared.observability.ApiErrors;
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
    public ResponseEntity<Object> adminReport() {
        if (patched.get("auth-required")) {
            return error(HttpStatus.UNAUTHORIZED, "Authentication required", "/demo/admin/report");
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
    public ResponseEntity<Object> debugConfig() {
        if (patched.get("disable-debug-endpoint")) {
            return error(HttpStatus.NOT_FOUND, "Debug endpoint is disabled", "/demo/debug/config");
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
    public ResponseEntity<Object> applyPatch(@PathVariable String patchName) {
        if (!patched.containsKey(patchName)) {
            return error(HttpStatus.NOT_FOUND, "Unknown sandbox patch: " + patchName, "/internal/patches/" + patchName);
        }
        patched.put(patchName, true);
        return ResponseEntity.ok(Map.of("patch", patchName, "status", "APPLIED"));
    }

    @PostMapping("/internal/patches/{patchName}/rollback")
    public ResponseEntity<Object> rollbackPatch(@PathVariable String patchName) {
        if (!patched.containsKey(patchName)) {
            return error(HttpStatus.NOT_FOUND, "Unknown sandbox patch: " + patchName, "/internal/patches/" + patchName + "/rollback");
        }
        patched.put(patchName, false);
        return ResponseEntity.ok(Map.of("patch", patchName, "status", "ROLLED_BACK"));
    }

    @GetMapping("/internal/patches/status")
    public ResponseEntity<Object> status() {
        return ResponseEntity.ok(Map.copyOf(patched));
    }

    private ResponseEntity<Object> error(HttpStatus status, String message, String path) {
        return ApiErrors.response(status, message, path);
    }
}
