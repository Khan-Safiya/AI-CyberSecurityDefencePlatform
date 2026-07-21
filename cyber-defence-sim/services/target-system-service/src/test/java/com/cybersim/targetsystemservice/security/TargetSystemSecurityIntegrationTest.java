package com.cybersim.targetsystemservice.security;

import com.cybersim.shared.security.ServiceJwtSupport;
import com.cybersim.targetsystemservice.controller.DemoTargetController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DemoTargetController.class, properties = {
        "SERVICE_AUTH_TOKEN=test-legacy-token",
        "service-jwt.secret=target-system-test-secret-at-least-32-bytes",
        "service-jwt.issuer=cybersim-services-test"
})
@Import(TargetSystemSecurityConfiguration.class)
class TargetSystemSecurityIntegrationTest {
    @Autowired private MockMvc mvc;

    @Test
    void remediationCanApplyAndVerificationCanReadStatus() throws Exception {
        mvc.perform(post("/internal/patches/auth-required").header("Authorization", bearer("SERVICE_REMEDIATION")))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/patches/status").header("Authorization", bearer("SERVICE_VERIFICATION")))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/patches/status").header("Authorization", bearer("SERVICE_RED_TEAM")))
                .andExpect(status().isOk());
    }

    @Test
    void wrongRoleAudienceAndLegacyTokenAreRejectedFromWorkerRoutes() throws Exception {
        mvc.perform(post("/internal/patches/auth-required").header("Authorization", bearer("SERVICE_VERIFICATION")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/patches/status").header("Authorization",
                        "Bearer " + token("SERVICE_VERIFICATION", "simulation-orchestrator-service")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/patches/status").header("X-Service-Token", "test-legacy-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void remediationIdentityProtectsRollback() throws Exception {
        mvc.perform(post("/internal/patches/auth-required/rollback"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/patches/auth-required/rollback")
                        .header("Authorization", bearer("SERVICE_REMEDIATION")))
                .andExpect(status().isOk());
        mvc.perform(post("/internal/patches/auth-required/rollback")
                        .header("X-Service-Token", "test-legacy-token"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(String role) {
        return "Bearer " + token(role, "target-system-service");
    }

    private String token(String role, String audience) {
        return ServiceJwtSupport.issuer("target-system-test-secret-at-least-32-bytes",
                "cybersim-services-test", "test-worker", role, audience).issue();
    }
}
