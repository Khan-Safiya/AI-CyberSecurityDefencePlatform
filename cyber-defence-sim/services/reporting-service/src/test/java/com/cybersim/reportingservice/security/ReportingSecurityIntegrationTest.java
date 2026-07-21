package com.cybersim.reportingservice.security;

import com.cybersim.reportingservice.controller.ReportingController;
import com.cybersim.shared.security.ServiceJwtSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportingController.class, properties = {
        "security.jwt.secret=reporting-active-secret-at-least-32-bytes",
        "security.jwt.previous-secret=reporting-previous-secret-at-least-32-bytes",
        "security.jwt.issuer=cybersim-test"
})
@Import(ReportingSecurityConfiguration.class)
class ReportingSecurityIntegrationTest {
    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID SIMULATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Autowired
    private MockMvc mvc;

    @Test
    void reportsRequireBearerToken() throws Exception {
        mvc.perform(get("/reports/" + REPORT_ID))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/reports/simulations/" + SIMULATION_ID + "/generate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void auditorCanReadButCannotGenerateReports() throws Exception {
        mvc.perform(get("/reports/" + REPORT_ID).header("Authorization", bearer("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REPORT_ID.toString()));
        mvc.perform(post("/reports/simulations/" + SIMULATION_ID + "/generate")
                        .header("Authorization", bearer("AUDITOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanGenerateReports() throws Exception {
        mvc.perform(post("/reports/simulations/" + SIMULATION_ID + "/generate")
                        .header("Authorization", bearer("SIMULATION_OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value(SIMULATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("GENERATED"));
    }

    @Test
    void previousSigningSecretIsAcceptedDuringRotation() throws Exception {
        mvc.perform(get("/reports/" + REPORT_ID).header("Authorization",
                        "Bearer " + token("AUDITOR", "reporting-previous-secret-at-least-32-bytes")))
                .andExpect(status().isOk());
    }

    private String bearer(String role) {
        return "Bearer " + token(role, "reporting-active-secret-at-least-32-bytes");
    }

    private String token(String role, String secret) {
        return ServiceJwtSupport.issuer(secret, "cybersim-test", "test-user", role, "reporting-service").issue();
    }
}
