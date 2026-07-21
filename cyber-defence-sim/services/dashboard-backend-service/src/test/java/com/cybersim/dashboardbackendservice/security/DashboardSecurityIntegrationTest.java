package com.cybersim.dashboardbackendservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import com.cybersim.dashboardbackendservice.controller.DashboardController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class, properties = {
        "security.jwt.secret=dashboard-test-secret-at-least-32-bytes-long",
        "security.jwt.issuer=cybersim-test"
})
@Import(DashboardSecurityConfiguration.class)
class DashboardSecurityIntegrationTest {
    private static final String SIMULATION_ID = "00000000-0000-0000-0000-000000000001";
    @Autowired
    private MockMvc mvc;

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mvc.perform(get("/dashboard/simulations/" + SIMULATION_ID + "/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyPlatformRoleCanReadDashboard() throws Exception {
        for (String role : new String[]{"ADMIN", "SIMULATION_OPERATOR", "AUDITOR"}) {
            mvc.perform(get("/dashboard/simulations/" + SIMULATION_ID + "/overview")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void dashboardRejectsWrites() throws Exception {
        mvc.perform(post("/dashboard/simulations/" + SIMULATION_ID + "/overview")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

}
