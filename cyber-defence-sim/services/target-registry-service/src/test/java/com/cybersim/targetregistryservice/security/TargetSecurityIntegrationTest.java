package com.cybersim.targetregistryservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import com.cybersim.shared.dto.TargetMode;
import com.cybersim.targetregistryservice.controller.TargetRegistryController;
import com.cybersim.targetregistryservice.model.TargetRecord;
import com.cybersim.targetregistryservice.store.TargetStore;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Test
    void serviceRolesCanReadInternalScopeEndpoint() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000005001");
        TargetRecord record = new TargetRecord(id, "Staging", "desc", TargetMode.EXTERNAL_STAGING_TARGET,
                "https://staging.example.com", "STAGING", List.of("staging.example.com"), List.of("/api"),
                List.of(), List.of("GET"), 60, true, "VERIFIED", "ACTIVE", "token", Instant.now(), Instant.now());
        when(targetStore.findById(id)).thenReturn(Optional.of(record));

        mvc.perform(get("/internal/targets/" + id).with(role("SERVICE_POLICY"))).andExpect(status().isOk());
        mvc.perform(get("/internal/targets/" + id).with(role("SERVICE_RED_TEAM"))).andExpect(status().isOk());
    }

    @Test
    void humanRolesCannotReadInternalScopeEndpoint() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000005002");

        mvc.perform(get("/internal/targets/" + id).with(role("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/internal/targets/" + id)).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
