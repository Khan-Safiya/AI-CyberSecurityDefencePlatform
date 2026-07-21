package com.cybersim.apigatewayservice.security;

import com.cybersim.shared.security.JwtSecuritySupport;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "gateway.jwt.secret=gateway-test-secret-at-least-32-bytes-long",
        "gateway.jwt.previous-secret=gateway-old-secret-at-least-32-bytes",
        "gateway.jwt.issuer=cybersim-test"
})
@AutoConfigureMockMvc
@Import(GatewaySecurityIntegrationTest.RoutesConfiguration.class)
class GatewaySecurityIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void publicIndexDoesNotRequireAuthentication() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void apiRoutesRequireAValidSignature() throws Exception {
        mvc.perform(get("/api/test")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test").header("Authorization", bearer(token(
                        "wrong-signing-secret-that-is-32-bytes-long", "AUDITOR"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void auditorCanReadButCannotWrite() throws Exception {
        String token = token("gateway-test-secret-at-least-32-bytes-long", "AUDITOR");
        mvc.perform(get("/api/test").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(post("/api/test").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanWriteButCannotUseAdminRoutes() throws Exception {
        String token = token("gateway-test-secret-at-least-32-bytes-long", "SIMULATION_OPERATOR");
        mvc.perform(post("/api/test").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/test").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUseAdminRoutes() throws Exception {
        String token = token("gateway-test-secret-at-least-32-bytes-long", "ADMIN");
        mvc.perform(get("/api/admin/test").header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    @Test
    void previousSigningSecretIsAcceptedDuringRotationWindow() throws Exception {
        String token = token("gateway-old-secret-at-least-32-bytes", "AUDITOR");
        mvc.perform(get("/api/test").header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    private String token(String secret, String role) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtSecuritySupport.secretKey(secret)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("cybersim-test").subject("test-user")
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("roles", List.of(role)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class RoutesConfiguration {
        @Bean
        TestRoutes testRoutes() {
            return new TestRoutes();
        }
    }

    @RestController
    static class TestRoutes {
        @GetMapping("/api/test")
        Map<String, String> read() {
            return Map.of("result", "read");
        }

        @PostMapping("/api/test")
        Map<String, String> write() {
            return Map.of("result", "write");
        }

        @GetMapping("/api/admin/test")
        Map<String, String> admin() {
            return Map.of("result", "admin");
        }
    }
}
