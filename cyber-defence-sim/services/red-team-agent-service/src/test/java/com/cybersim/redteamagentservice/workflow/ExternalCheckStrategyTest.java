package com.cybersim.redteamagentservice.workflow;

import com.cybersim.redteamagentservice.probe.ExternalProbeClient;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.dto.SimulationResponse;
import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.dto.TargetResponse;
import com.cybersim.shared.dto.VulnerabilityCreateRequest;
import com.cybersim.shared.http.SafeHttpRequest;
import com.cybersim.shared.http.SafeHttpResponse;
import com.cybersim.shared.http.SafeOutboundHttpException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalCheckStrategyTest {
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");

    @Test
    void cleanTargetProducesNoFindings() {
        FakeProbeClient probeClient = new FakeProbeClient(new SafeHttpResponse(429,
                Map.of("Strict-Transport-Security", List.of("max-age=63072000"),
                        "X-Content-Type-Options", List.of("nosniff")),
                "ok".getBytes()));
        FakePolicyClient policyClient = new FakePolicyClient();

        int findings = new ExternalCheckStrategy(probeClient).run(policyClient, MESSAGE_ID, SIMULATION_ID, ROUND_ID, target());

        assertThat(findings).isZero();
        assertThat(policyClient.findings).isEmpty();
        assertThat(probeClient.requests).isNotEmpty();
    }

    @Test
    void flagsMissingAuthenticationWhenPathRespondsWithoutCredentials() {
        FakeProbeClient probeClient = new FakeProbeClient(new SafeHttpResponse(200,
                Map.of("Strict-Transport-Security", List.of("max-age=63072000"),
                        "X-Content-Type-Options", List.of("nosniff")),
                "welcome".getBytes()));
        FakePolicyClient policyClient = new FakePolicyClient();

        new ExternalCheckStrategy(probeClient).run(policyClient, MESSAGE_ID, SIMULATION_ID, ROUND_ID, target());

        assertThat(policyClient.findings).extracting(VulnerabilityCreateRequest::type).contains("AUTHENTICATION");
    }

    @Test
    void flagsMissingSecurityHeaders() {
        FakeProbeClient probeClient = new FakeProbeClient(new SafeHttpResponse(401, Map.of(), "denied".getBytes()));
        FakePolicyClient policyClient = new FakePolicyClient();

        new ExternalCheckStrategy(probeClient).run(policyClient, MESSAGE_ID, SIMULATION_ID, ROUND_ID, target());

        assertThat(policyClient.findings).extracting(VulnerabilityCreateRequest::type).contains("CONFIG_EXPOSURE");
    }

    @Test
    void flagsExposedDebugInformation() {
        FakeProbeClient probeClient = new FakeProbeClient(new SafeHttpResponse(500,
                Map.of("Strict-Transport-Security", List.of("max-age=1"), "X-Content-Type-Options", List.of("nosniff")),
                "Internal Server Error: stack trace at line 42".getBytes()));
        FakePolicyClient policyClient = new FakePolicyClient();

        new ExternalCheckStrategy(probeClient).run(policyClient, MESSAGE_ID, SIMULATION_ID, ROUND_ID, target());

        assertThat(policyClient.findings.stream().filter(f -> f.type().equals("CONFIG_EXPOSURE")).count())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void flagsMissingRateLimitWhenNoAttemptIsThrottled() {
        FakeProbeClient probeClient = new FakeProbeClient(new SafeHttpResponse(401,
                Map.of("Strict-Transport-Security", List.of("max-age=1"), "X-Content-Type-Options", List.of("nosniff")),
                "denied".getBytes()));
        FakePolicyClient policyClient = new FakePolicyClient();

        new ExternalCheckStrategy(probeClient).run(policyClient, MESSAGE_ID, SIMULATION_ID, ROUND_ID, target());

        assertThat(policyClient.findings).extracting(VulnerabilityCreateRequest::type).contains("RATE_LIMIT");
    }

    @Test
    void skipsAllProbesWhenPolicyDeniesEverything() {
        FakeProbeClient probeClient = new FakeProbeClient(new SafeHttpResponse(200, Map.of(), "x".getBytes()));
        FakePolicyClient policyClient = new FakePolicyClient();
        policyClient.allow = false;

        int findings = new ExternalCheckStrategy(probeClient).run(policyClient, MESSAGE_ID, SIMULATION_ID, ROUND_ID, target());

        assertThat(findings).isZero();
        assertThat(probeClient.requests).isEmpty();
    }

    @Test
    void skipsProbeGracefullyWhenOutboundCallFails() {
        ExternalProbeClient throwingClient = request -> {
            throw new SafeOutboundHttpException("connection refused");
        };
        FakePolicyClient policyClient = new FakePolicyClient();

        int findings = new ExternalCheckStrategy(throwingClient).run(policyClient, MESSAGE_ID, SIMULATION_ID, ROUND_ID, target());

        assertThat(findings).isZero();
    }

    @Test
    void returnsZeroWhenTargetHasNoAllowedPaths() {
        FakeProbeClient probeClient = new FakeProbeClient(new SafeHttpResponse(200, Map.of(), "x".getBytes()));
        FakePolicyClient policyClient = new FakePolicyClient();
        TargetResponse noPathsTarget = new TargetResponse(UUID.randomUUID(), "Staging", TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com", "STAGING", List.of("staging.example.com"), List.of(), List.of(),
                List.of("GET"), "VERIFIED", "ACTIVE", "token", Instant.now());

        int findings = new ExternalCheckStrategy(probeClient).run(policyClient, MESSAGE_ID, SIMULATION_ID, ROUND_ID, noPathsTarget);

        assertThat(findings).isZero();
        assertThat(probeClient.requests).isEmpty();
    }

    @Test
    void probesOnlyTheDeclaredScopePathSkippingExcludedCandidates() {
        FakeProbeClient probeClient = new FakeProbeClient(new SafeHttpResponse(429,
                Map.of("Strict-Transport-Security", List.of("max-age=1"), "X-Content-Type-Options", List.of("nosniff")),
                "ok".getBytes()));
        FakePolicyClient policyClient = new FakePolicyClient();
        TargetResponse scopedTarget = new TargetResponse(UUID.randomUUID(), "Staging", TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com", "STAGING", List.of("staging.example.com"),
                List.of("/admin/**", "/api"), List.of("/admin"), List.of("GET"), "VERIFIED", "ACTIVE", "token", Instant.now());

        new ExternalCheckStrategy(probeClient).run(policyClient, MESSAGE_ID, SIMULATION_ID, ROUND_ID, scopedTarget);

        assertThat(probeClient.requests).allSatisfy(request -> assertThat(request.uri().getPath()).isEqualTo("/api"));
        assertThat(policyClient.policyRequests).allSatisfy(request -> assertThat(request.path()).isEqualTo("/api"));
    }

    private static TargetResponse target() {
        return new TargetResponse(UUID.randomUUID(), "Staging", TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com", "STAGING", List.of("staging.example.com"), List.of("/api"),
                List.of(), List.of("GET"), "VERIFIED", "ACTIVE", "token", Instant.now());
    }

    private static final class FakeProbeClient implements ExternalProbeClient {
        private final SafeHttpResponse response;
        private final List<SafeHttpRequest> requests = new ArrayList<>();

        private FakeProbeClient(SafeHttpResponse response) {
            this.response = response;
        }

        @Override
        public SafeHttpResponse send(SafeHttpRequest request) {
            requests.add(request);
            return response;
        }
    }

    private static final class FakePolicyClient implements RedTeamWorkflowClient {
        private final List<PolicyEvaluationRequest> policyRequests = new ArrayList<>();
        private final List<VulnerabilityCreateRequest> findings = new ArrayList<>();
        private boolean allow = true;

        @Override
        public SimulationResponse simulation(UUID simulationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Boolean> sandboxPatchStates() {
            return new LinkedHashMap<>();
        }

        @Override
        public Optional<TargetResponse> target(UUID targetId) {
            return Optional.empty();
        }

        @Override
        public boolean policyAllows(PolicyEvaluationRequest request) {
            policyRequests.add(request);
            return allow;
        }

        @Override
        public void createFinding(UUID idempotencyKey, VulnerabilityCreateRequest request) {
            findings.add(request);
        }

        @Override
        public void completeRedTeamStage(UUID simulationId, UUID roundId) {
        }
    }
}
