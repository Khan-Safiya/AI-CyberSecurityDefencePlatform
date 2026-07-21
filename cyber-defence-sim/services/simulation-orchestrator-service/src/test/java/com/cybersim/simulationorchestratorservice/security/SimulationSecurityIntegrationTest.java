package com.cybersim.simulationorchestratorservice.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import com.cybersim.simulationorchestratorservice.controller.RoundController;
import com.cybersim.simulationorchestratorservice.controller.SimulationController;
import com.cybersim.simulationorchestratorservice.outbox.OutboxEventFactory;
import com.cybersim.simulationorchestratorservice.outbox.OutboxStore;
import com.cybersim.simulationorchestratorservice.store.SimulationRoundStore;
import com.cybersim.simulationorchestratorservice.store.SimulationStore;
import com.cybersim.shared.security.ServiceJwtSupport;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SimulationController.class, RoundController.class}, properties = {
        "security.jwt.secret=simulation-test-secret-at-least-32-bytes-long",
        "security.jwt.issuer=cybersim-test",
        "simulation.service-auth-token=test-service-token",
        "service-jwt.secret=service-test-secret-at-least-32-bytes-long",
        "service-jwt.issuer=cybersim-services-test"
})
@Import(SimulationSecurityConfiguration.class)
class SimulationSecurityIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean private SimulationStore simulationStore;
    @MockitoBean private SimulationRoundStore roundStore;
    @MockitoBean private OutboxStore outboxStore;
    @MockitoBean private OutboxEventFactory outboxEventFactory;

    @BeforeEach
    void setUp() {
        when(simulationStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(roundStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void directUserRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/simulations/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/simulations")).andExpect(status().isUnauthorized());
    }

    @Test
    void auditorCanReadButCannotStartSimulation() throws Exception {
        mvc.perform(get("/simulations/00000000-0000-0000-0000-000000000001").with(role("AUDITOR")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/simulations").with(role("AUDITOR"))).andExpect(status().isForbidden());
    }

    @Test
    void operatorCanStartSimulation() throws Exception {
        mvc.perform(post("/simulations").with(role("SIMULATION_OPERATOR")))
                .andExpect(status().isCreated());
    }

    @Test
    void internalRoundCommandRemainsOnControllerServiceTokenBoundary() throws Exception {
        mvc.perform(post("/simulations/00000000-0000-0000-0000-000000000001/rounds/" +
                        "00000000-0000-0000-0000-000000000002/red-team-complete"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyServiceTokenCannotReadSimulation() throws Exception {
        mvc.perform(get("/simulations/00000000-0000-0000-0000-000000000001")
                        .header("X-Service-Token", "test-service-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidServiceTokenCannotReadSimulation() throws Exception {
        mvc.perform(get("/simulations/00000000-0000-0000-0000-000000000001")
                        .header("X-Service-Token", "wrong-service-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void redTeamServiceIdentityCanReadAndCompleteItsStage() throws Exception {
        String token = serviceToken("SERVICE_RED_TEAM", "simulation-orchestrator-service");

        mvc.perform(get("/simulations/00000000-0000-0000-0000-000000000001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/simulations/00000000-0000-0000-0000-000000000001/rounds/" +
                        "00000000-0000-0000-0000-000000000002/red-team-complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongServiceRoleAndAudienceCannotCompleteRedTeamStage() throws Exception {
        String wrongRole = serviceToken("SERVICE_SCORING", "simulation-orchestrator-service");
        String wrongAudience = serviceToken("SERVICE_RED_TEAM", "target-system-service");
        String uri = "/simulations/00000000-0000-0000-0000-000000000001/rounds/" +
                "00000000-0000-0000-0000-000000000002/red-team-complete";

        mvc.perform(post(uri).header("Authorization", "Bearer " + wrongRole))
                .andExpect(status().isForbidden());
        mvc.perform(post(uri).header("Authorization", "Bearer " + wrongAudience))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(uri).header("X-Service-Token", "test-service-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyDetectionServiceIdentityCanCompleteDetectionStage() throws Exception {
        String uri = "/simulations/00000000-0000-0000-0000-000000000001/rounds/" +
                "00000000-0000-0000-0000-000000000002/detection-complete";

        mvc.perform(post(uri).header("Authorization", "Bearer " +
                        serviceToken("SERVICE_DETECTION", "simulation-orchestrator-service")))
                .andExpect(status().isNotFound());
        mvc.perform(post(uri).header("Authorization", "Bearer " +
                        serviceToken("SERVICE_RED_TEAM", "simulation-orchestrator-service")))
                .andExpect(status().isForbidden());
        mvc.perform(post(uri).header("X-Service-Token", "test-service-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyRemediationServiceIdentityCanCompleteBlueTeamStage() throws Exception {
        String uri = "/simulations/00000000-0000-0000-0000-000000000001/rounds/" +
                "00000000-0000-0000-0000-000000000002/blue-team-complete";

        mvc.perform(post(uri).header("Authorization", "Bearer " +
                        serviceToken("SERVICE_REMEDIATION", "simulation-orchestrator-service")))
                .andExpect(status().isNotFound());
        mvc.perform(post(uri).header("Authorization", "Bearer " +
                        serviceToken("SERVICE_DETECTION", "simulation-orchestrator-service")))
                .andExpect(status().isForbidden());
        mvc.perform(post(uri).header("X-Service-Token", "test-service-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyVerificationServiceIdentityCanCompleteVerificationStage() throws Exception {
        String uri = "/simulations/00000000-0000-0000-0000-000000000001/rounds/" +
                "00000000-0000-0000-0000-000000000002/verification-complete";

        mvc.perform(post(uri).header("Authorization", "Bearer " +
                        serviceToken("SERVICE_VERIFICATION", "simulation-orchestrator-service")))
                .andExpect(status().isNotFound());
        mvc.perform(post(uri).header("Authorization", "Bearer " +
                        serviceToken("SERVICE_REMEDIATION", "simulation-orchestrator-service")))
                .andExpect(status().isForbidden());
        mvc.perform(post(uri).header("X-Service-Token", "test-service-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyScoringServiceIdentityCanReadAndCompleteRound() throws Exception {
        String token = serviceToken("SERVICE_SCORING", "simulation-orchestrator-service");
        String completeUri = "/simulations/00000000-0000-0000-0000-000000000001/rounds/" +
                "00000000-0000-0000-0000-000000000002/complete";

        mvc.perform(get("/simulations/00000000-0000-0000-0000-000000000001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post(completeUri).header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(completeUri).header("Authorization", "Bearer " +
                        serviceToken("SERVICE_VERIFICATION", "simulation-orchestrator-service"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(completeUri).header("X-Service-Token", "test-service-token")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyOrchestratorControlIdentityCanAdvanceRound() throws Exception {
        String uri = "/simulations/00000000-0000-0000-0000-000000000001/rounds/" +
                "00000000-0000-0000-0000-000000000002/advance";
        mvc.perform(post(uri).header("Authorization", "Bearer " +
                        serviceToken("SERVICE_ORCHESTRATOR_CONTROL", "simulation-orchestrator-service")))
                .andExpect(status().isNotFound());
        mvc.perform(post(uri).header("Authorization", "Bearer " +
                        serviceToken("SERVICE_SCORING", "simulation-orchestrator-service")))
                .andExpect(status().isForbidden());
        mvc.perform(post(uri).header("X-Service-Token", "test-service-token"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String serviceToken(String role, String audience) {
        return ServiceJwtSupport.issuer("service-test-secret-at-least-32-bytes-long",
                "cybersim-services-test", "test-worker", role, audience).issue();
    }
}
