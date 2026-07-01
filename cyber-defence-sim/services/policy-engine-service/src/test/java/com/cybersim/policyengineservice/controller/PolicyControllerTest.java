package com.cybersim.policyengineservice.controller;

import com.cybersim.shared.dto.PolicyDecisionResponse;
import com.cybersim.shared.dto.PolicyEvaluationRequest;
import com.cybersim.shared.exceptions.GlobalApiExceptionHandler;
import com.cybersim.shared.observability.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PolicyControllerTest {
    private PolicyController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new PolicyController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void emptyPolicyEvaluationIsDeniedByDefault() {
        PolicyDecisionResponse response = controller.evaluate(null);

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("missing evaluation request");
        assertThat(response.policyVersion()).isEqualTo("baseline-safe-policy-v1");
    }

    @Test
    void safeSandboxActionInsideScopeIsAllowed() {
        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                UUID.randomUUID(),
                "SIMULATED_AUTH_REQUIRED_CHECK",
                "target-system-service",
                "/demo/admin/report",
                "GET"
        ));

        assertThat(response.allowed()).isTrue();
    }

    @Test
    void unknownActionOutsideScopeIsDenied() {
        PolicyDecisionResponse response = controller.evaluate(new PolicyEvaluationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "REAL_NETWORK_SCAN",
                "example.com",
                "/",
                "GET"
        ));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("Denied by default policy");
    }

    @Test
    void malformedPolicyRequestIsRejected() throws Exception {
        mockMvc.perform(post("/policies/evaluate-action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"SIMULATED_AUTH_REQUIRED_CHECK\",\"host\":\"bad host\",\"path\":\"demo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}
