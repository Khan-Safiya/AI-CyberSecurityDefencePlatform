package com.cybersim.identityservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity_service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.default_schema=identity_service",
        "spring.flyway.enabled=true",
        "spring.flyway.default-schema=identity_service",
        "spring.flyway.schemas=identity_service",
        "identity.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "identity.jwt.active-key-id=test-current",
        "identity.users.admin-password=admin-test-password",
        "identity.users.operator-password=operator-test-password",
        "identity.users.auditor-password=auditor-test-password"
})
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void loginIssuesSignedJwtWithIdentityAndRoleClaims() throws Exception {
        String token = login("demo-operator", "operator-test-password");
        var jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("demo-operator");
        assertThat(jwt.getHeaders()).containsEntry("kid", "test-current");
        assertThat(jwt.getClaimAsString("userId")).isEqualTo("00000000-0000-0000-0000-000000000002");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SIMULATION_OPERATOR");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void rejectsInvalidCredentialsAndRequiresBearerTokenForProfile() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo-admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanReadOwnTokenIdentity() throws Exception {
        String token = login("demo-auditor", "auditor-test-password");
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo-auditor"))
                .andExpect(jsonPath("$.roles[0]").value("AUDITOR"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void onlyAdminCanListUsers() throws Exception {
        String auditorToken = login("demo-auditor", "auditor-test-password");
        mvc.perform(get("/auth/users").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        String adminToken = login("demo-admin", "admin-test-password");
        mvc.perform(get("/auth/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void adminCanDisableEnableAndResetPassword() throws Exception {
        String adminToken = login("demo-admin", "admin-test-password");

        mvc.perform(post("/auth/users/demo-auditor/disable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo-auditor\",\"password\":\"auditor-test-password\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/auth/users/demo-auditor/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(post("/auth/users/demo-auditor/password").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"new-auditor-password\"}"))
                .andExpect(status().isOk());

        login("demo-auditor", "new-auditor-password");

        mvc.perform(post("/auth/users/demo-auditor/password").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"auditor-test-password\"}"))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }
}
