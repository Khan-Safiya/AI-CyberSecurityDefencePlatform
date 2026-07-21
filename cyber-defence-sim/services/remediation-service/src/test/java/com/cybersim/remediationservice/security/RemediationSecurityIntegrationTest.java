package com.cybersim.remediationservice.security;

import com.cybersim.remediationservice.controller.RemediationVerificationController;
import com.cybersim.remediationservice.store.RemediationStore;
import com.cybersim.shared.security.ServiceJwtSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RemediationVerificationController.class, properties = {
        "service-jwt.secret=sync-test-secret-at-least-32-bytes-long",
        "service-jwt.issuer=cybersim-services-test"
})
@Import(RemediationSecurityConfiguration.class)
class RemediationSecurityIntegrationTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private RemediationStore store;

    @Test
    void onlyVerificationIdentityCanSubmitResult() throws Exception {
        String uri = "/internal/remediations/00000000-0000-0000-0000-000000000001/verification-result";
        mvc.perform(post(uri).header("Authorization", bearer("SERVICE_VERIFICATION", "remediation-service"))
                        .contentType("application/json").content("{\"status\":\"PASSED\",\"evidenceSummary\":\"ok\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(uri).header("Authorization", bearer("SERVICE_REMEDIATION", "remediation-service"))
                        .contentType("application/json").content("{\"status\":\"PASSED\",\"evidenceSummary\":\"ok\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(uri).header("X-Service-Token", "legacy")
                        .contentType("application/json").content("{\"status\":\"PASSED\",\"evidenceSummary\":\"ok\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(String role, String audience) {
        return "Bearer " + ServiceJwtSupport.issuer("sync-test-secret-at-least-32-bytes-long",
                "cybersim-services-test", "verification-service", role, audience).issue();
    }
}
