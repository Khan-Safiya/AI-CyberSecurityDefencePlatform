package com.cybersim.scoringservice.security;

import com.cybersim.scoringservice.controller.ScoringController;
import com.cybersim.scoringservice.store.ScoreAppendResult;
import com.cybersim.scoringservice.store.ScoreEventStore;
import com.cybersim.shared.security.ServiceJwtSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = ScoringController.class, properties = {
        "service-jwt.secret=scoring-security-test-secret-at-least-32-bytes",
        "service-jwt.issuer=cybersim-services-test"
})
@Import(ScoringSecurityConfiguration.class)
class ScoringSecurityIntegrationTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ScoreEventStore store;

    @BeforeEach
    void setUp() {
        when(store.append(any())).thenAnswer(invocation ->
                new ScoreAppendResult(invocation.getArgument(0), true));
    }

    @Test
    void scoreSubmissionRequiresDedicatedRoleAndAudience() throws Exception {
        String body = "{\"simulationId\":\"00000000-0000-0000-0000-000000000001\"," +
                "\"sourceEventId\":\"00000000-0000-0000-0000-000000000002\"," +
                "\"scoreType\":\"BLUE_PATCH_APPLIED\"}";

        mvc.perform(post("/internal/score-events").header("Authorization",
                        bearer("SERVICE_SCORE_PRODUCER", "scoring-service"))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/internal/score-events").header("Authorization",
                        bearer("SERVICE_SCORING", "scoring-service"))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/score-events").header("Authorization",
                        bearer("SERVICE_SCORE_PRODUCER", "simulation-orchestrator-service"))
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/score-events").header("X-Service-Token", "legacy")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(String role, String audience) {
        return "Bearer " + ServiceJwtSupport.issuer("scoring-security-test-secret-at-least-32-bytes",
                "cybersim-services-test", "test-producer", role, audience).issue();
    }
}
