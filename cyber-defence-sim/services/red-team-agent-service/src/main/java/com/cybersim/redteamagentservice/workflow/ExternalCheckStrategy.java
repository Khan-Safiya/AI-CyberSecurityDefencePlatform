package com.cybersim.redteamagentservice.workflow;

import com.cybersim.redteamagentservice.probe.ExternalProbeClient;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.TargetResponse;
import com.cybersim.shared.dto.VulnerabilityCreateRequest;
import com.cybersim.shared.http.SafeHttpRequest;
import com.cybersim.shared.http.SafeHttpResponse;
import com.cybersim.shared.http.SafeOutboundHttpException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Runs a small, fixed set of safe, read-only, black-box probes against a verified
 * {@code EXTERNAL_STAGING_TARGET}, reusing the same declared scope (host/paths/methods) the
 * target owner registered. Every probe is still gated by a per-request policy check, and a probe
 * that the policy engine denies (or that fails to connect) is skipped rather than treated as a
 * hard failure - unlike the built-in sandbox, denial or unreachability of an external target is an
 * expected, non-fatal outcome.
 */
@Component
class ExternalCheckStrategy {
    private static final List<String> DEBUG_MARKERS = List.of(
            "stack trace", "traceback", "debug=true", "whitelabel error page", "internal server error");
    private static final int RATE_LIMIT_PROBE_ATTEMPTS = 5;

    private final ExternalProbeClient probeClient;

    ExternalCheckStrategy(ExternalProbeClient probeClient) {
        this.probeClient = probeClient;
    }

    int run(RedTeamWorkflowClient client, UUID messageId, UUID simulationId, UUID roundId, TargetResponse target) {
        if (target.allowedPaths().isEmpty() || !target.allowedHttpMethods().stream().anyMatch("GET"::equalsIgnoreCase)) {
            return 0;
        }
        String probePath = firstProbeablePath(target);
        if (probePath == null) {
            return 0;
        }
        String host = URI.create(target.baseUrl()).getHost();
        URI probeUri = URI.create(target.baseUrl()).resolve(probePath);

        int findings = 0;
        findings += runMissingAuthProbe(client, messageId, simulationId, roundId, target, host, probePath, probeUri);
        findings += runSecurityHeadersProbe(client, messageId, simulationId, roundId, target, host, probePath, probeUri);
        findings += runDebugExposureProbe(client, messageId, simulationId, roundId, target, host, probePath, probeUri);
        findings += runRateLimitProbe(client, messageId, simulationId, roundId, target, host, probePath, probeUri);
        return findings;
    }

    private int runMissingAuthProbe(RedTeamWorkflowClient client, UUID messageId, UUID simulationId, UUID roundId,
                                     TargetResponse target, String host, String probePath, URI probeUri) {
        if (!policyAllows(client, simulationId, target, host, probePath, "SIMULATED_EXTERNAL_AUTH_REQUIRED_CHECK")) {
            return 0;
        }
        SafeHttpResponse response = safeGet(probeUri);
        if (response == null || response.statusCode() != 200) {
            return 0;
        }
        return recordFinding(client, messageId, simulationId, roundId, target, "external-missing-auth",
                "Missing authentication on external target", "AUTHENTICATION", "HIGH",
                "The declared path " + probePath + " on " + host + " responded 200 without credentials.",
                probePath, "Require authentication before serving this path.");
    }

    private int runSecurityHeadersProbe(RedTeamWorkflowClient client, UUID messageId, UUID simulationId, UUID roundId,
                                         TargetResponse target, String host, String probePath, URI probeUri) {
        if (!policyAllows(client, simulationId, target, host, probePath, "SIMULATED_EXTERNAL_SECURITY_HEADERS_CHECK")) {
            return 0;
        }
        SafeHttpResponse response = safeGet(probeUri);
        if (response == null) {
            return 0;
        }
        boolean missingHsts = !response.hasHeader("Strict-Transport-Security");
        boolean missingContentTypeOptions = !response.hasHeader("X-Content-Type-Options");
        if (!missingHsts && !missingContentTypeOptions) {
            return 0;
        }
        return recordFinding(client, messageId, simulationId, roundId, target, "external-missing-security-headers",
                "Missing recommended security headers", "CONFIG_EXPOSURE", "LOW",
                "The declared path " + probePath + " on " + host + " is missing Strict-Transport-Security and/or X-Content-Type-Options.",
                probePath, "Add the missing security response headers.");
    }

    private int runDebugExposureProbe(RedTeamWorkflowClient client, UUID messageId, UUID simulationId, UUID roundId,
                                       TargetResponse target, String host, String probePath, URI probeUri) {
        if (!policyAllows(client, simulationId, target, host, probePath, "SIMULATED_EXTERNAL_CONFIG_EXPOSURE_CHECK")) {
            return 0;
        }
        SafeHttpResponse response = safeGet(probeUri);
        if (response == null) {
            return 0;
        }
        boolean exposesDebugInfo = DEBUG_MARKERS.stream().anyMatch(response::bodyContainsIgnoreCase);
        if (!exposesDebugInfo) {
            return 0;
        }
        return recordFinding(client, messageId, simulationId, roundId, target, "external-exposed-debug-info",
                "Exposed debug or error detail", "CONFIG_EXPOSURE", "MEDIUM",
                "The declared path " + probePath + " on " + host + " returned a response containing debug or stack trace detail.",
                probePath, "Disable verbose error output in this environment.");
    }

    private int runRateLimitProbe(RedTeamWorkflowClient client, UUID messageId, UUID simulationId, UUID roundId,
                                   TargetResponse target, String host, String probePath, URI probeUri) {
        if (!policyAllows(client, simulationId, target, host, probePath, "SIMULATED_EXTERNAL_RATE_LIMIT_CHECK")) {
            return 0;
        }
        boolean anyThrottled = false;
        boolean anySucceeded = false;
        for (int attempt = 0; attempt < RATE_LIMIT_PROBE_ATTEMPTS; attempt++) {
            SafeHttpResponse response = safeGet(probeUri);
            if (response == null) {
                continue;
            }
            anySucceeded = true;
            if (response.statusCode() == 429) {
                anyThrottled = true;
                break;
            }
        }
        if (!anySucceeded || anyThrottled) {
            return 0;
        }
        return recordFinding(client, messageId, simulationId, roundId, target, "external-missing-rate-limit",
                "Missing rate limiting on external target", "RATE_LIMIT", "MEDIUM",
                "The declared path " + probePath + " on " + host + " did not return HTTP 429 across "
                        + RATE_LIMIT_PROBE_ATTEMPTS + " rapid requests.",
                probePath, "Add rate limiting to this path.");
    }

    private boolean policyAllows(RedTeamWorkflowClient client, UUID simulationId, TargetResponse target,
                                  String host, String probePath, String actionType) {
        PolicyEvaluationRequest policyRequest = new PolicyEvaluationRequest(simulationId, target.id(),
                RedTeamStageProcessor.RED_TEAM_AGENT_ID, actionType, host, probePath, "GET");
        return client.policyAllows(policyRequest);
    }

    private SafeHttpResponse safeGet(URI uri) {
        try {
            return probeClient.send(SafeHttpRequest.get(uri));
        } catch (SafeOutboundHttpException exception) {
            return null;
        }
    }

    private int recordFinding(RedTeamWorkflowClient client, UUID messageId, UUID simulationId, UUID roundId,
                               TargetResponse target, String checkKey, String title, String type, String severity,
                               String description, String probePath, String expectedFix) {
        UUID findingId = UUID.nameUUIDFromBytes((messageId + ":" + checkKey).getBytes(StandardCharsets.UTF_8));
        client.createFinding(findingId, new VulnerabilityCreateRequest(
                simulationId, roundId, target.id(), title, description, type, severity,
                "Read-only probe against the declared external target scope reports this check as vulnerable.",
                "Probe only the declared scope path " + probePath + " on the registered target.", probePath,
                expectedFix, RedTeamStageProcessor.RED_TEAM_AGENT_ID, null));
        return 1;
    }

    private static String firstProbeablePath(TargetResponse target) {
        for (String path : target.allowedPaths()) {
            String candidate = path.endsWith("/**") ? path.substring(0, path.length() - 3) : path;
            if (candidate.isBlank()) {
                candidate = "/";
            }
            if (isExcluded(target, candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static boolean isExcluded(TargetResponse target, String path) {
        return target.excludedPaths() != null
                && target.excludedPaths().stream().anyMatch(path::startsWith);
    }
}
