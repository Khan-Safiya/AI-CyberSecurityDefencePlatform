package com.cybersim.identityservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",
        "identity.jwt.secret=postgres-it-secret-that-is-at-least-32-bytes",
        "identity.jwt.active-key-id=postgres-it-current",
        "identity.users.admin-password=postgres-admin-password",
        "identity.users.operator-password=postgres-operator-password",
        "identity.users.auditor-password=postgres-auditor-password"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "cybersim.postgres.it", matches = "true")
class AuthControllerPostgresIT {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        String host = System.getProperty("cybersim.postgres.host", "127.0.0.1");
        String port = System.getProperty("cybersim.postgres.port", "5432");
        String db = System.getProperty("cybersim.postgres.db", "cybersim");
        String schema = System.getProperty("cybersim.postgres.schema", "identity_service");
        String username = System.getProperty("cybersim.postgres.user", "cybersim");
        String password = System.getProperty("cybersim.postgres.password", "cybersim_local_only");

        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + host + ":" + port + "/" + db);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.flyway.schemas", () -> schema);
    }

    @Test
    void realPostgresPersistsSeededUsersAndCredentialLifecycle() throws Exception {
        String operatorToken = login("demo-operator", "postgres-operator-password");
        var operatorJwt = jwtDecoder.decode(operatorToken);
        assertThat(operatorJwt.getHeaders()).containsEntry("kid", "postgres-it-current");
        assertThat(operatorJwt.getClaimAsStringList("roles")).containsExactly("SIMULATION_OPERATOR");

        mvc.perform(get("/auth/users").header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        String adminToken = login("demo-admin", "postgres-admin-password");
        mvc.perform(get("/auth/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mvc.perform(post("/auth/users/demo-auditor/disable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo-auditor\",\"password\":\"postgres-auditor-password\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/auth/users/demo-auditor/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        login("demo-auditor", "postgres-auditor-password");
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
