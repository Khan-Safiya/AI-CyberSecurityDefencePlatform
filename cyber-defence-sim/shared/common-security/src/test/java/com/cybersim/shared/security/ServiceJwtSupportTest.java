package com.cybersim.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceJwtSupportTest {
    private static final String SECRET = "service-jwt-test-secret-at-least-32-bytes-long";

    @Test
    void issuesShortLivedServiceIdentityWithRoleAndAudience() {
        String token = ServiceJwtSupport.issuer(
                SECRET, "cybersim-services", "scoring-service", "SERVICE_SCORING", "simulation-orchestrator"
        ).issue();

        Jwt jwt = JwtSecuritySupport.decoder(
                JwtSecuritySupport.secretKey(SECRET), "cybersim-services"
        ).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("scoring-service");
        assertThat(jwt.getAudience()).containsExactly("simulation-orchestrator");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SERVICE_SCORING");
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("service");
        assertThat(jwt.getExpiresAt()).isBeforeOrEqualTo(jwt.getIssuedAt().plusSeconds(120));
    }

    @Test
    void tokenCannotBeValidatedWithUserJwtSecret() {
        String token = ServiceJwtSupport.issuer(
                SECRET, "cybersim-services", "red-team-service", "SERVICE_RED_TEAM", "simulation-orchestrator"
        ).issue();

        assertThatThrownBy(() -> JwtSecuritySupport.decoder(
                JwtSecuritySupport.secretKey("different-user-jwt-secret-at-least-32-bytes"), "cybersim-local"
        ).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenCannotBeUsedAtAServiceOutsideItsAudience() {
        String token = ServiceJwtSupport.issuer(
                SECRET, "cybersim-services", "red-team-service", "SERVICE_RED_TEAM", "simulation-orchestrator"
        ).issue();

        assertThatThrownBy(() -> JwtSecuritySupport.decoder(
                JwtSecuritySupport.secretKey(SECRET), "cybersim-services", "target-system"
        ).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rotatingDecoderAcceptsActiveAndPreviousUserSecrets() {
        String activeSecret = "active-user-secret-at-least-32-bytes";
        String previousSecret = "previous-user-secret-at-least-32-bytes";
        var decoder = JwtSecuritySupport.rotatingDecoder(activeSecret, previousSecret, "cybersim-local");

        assertThat(decoder.decode(userToken(activeSecret)).getSubject()).isEqualTo("demo-user");
        assertThat(decoder.decode(userToken(previousSecret)).getSubject()).isEqualTo("demo-user");
    }

    @Test
    void rotatingDecoderRejectsUnknownUserSecret() {
        var decoder = JwtSecuritySupport.rotatingDecoder(
                "active-user-secret-at-least-32-bytes",
                "previous-user-secret-at-least-32-bytes",
                "cybersim-local"
        );

        assertThatThrownBy(() -> decoder.decode(userToken("unknown-user-secret-at-least-32-bytes")))
                .isInstanceOf(JwtException.class);
    }

    private String userToken(String secret) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtSecuritySupport.secretKey(secret)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cybersim-local")
                .subject("demo-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of("AUDITOR"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
