package com.cybersim.targetregistryservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import com.cybersim.targetregistryservice.controller.TargetRegistryController;
import com.cybersim.targetregistryservice.store.TargetStore;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TargetRegistryController.class, properties = {
        "security.jwt.secret=target-test-secret-at-least-32-bytes-long",
        "security.jwt.issuer=cybersim-test"
})
@Import(TargetSecurityConfiguration.class)
class TargetSecurityIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TargetStore targetStore;

    @BeforeEach
    void setUp() {
        when(targetStore.findAll()).thenReturn(List.of());
    }

    @Test
    void directReadsRequireAuthentication() throws Exception {
        mvc.perform(get("/targets")).andExpect(status().isUnauthorized());
    }

    @Test
    void auditorCanReadButCannotChangeTargets() throws Exception {
        mvc.perform(get("/targets").with(role("AUDITOR"))).andExpect(status().isOk());
        mvc.perform(post("/targets").with(role("AUDITOR"))).andExpect(status().isForbidden());
    }

    @Test
    void operatorCanChangeTargets() throws Exception {
        mvc.perform(post("/targets").with(role("SIMULATION_OPERATOR")))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
