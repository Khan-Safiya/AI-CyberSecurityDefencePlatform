package com.cybersim.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ServiceJwtSupport {
    public static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(2);

    private ServiceJwtSupport() {
    }

    public static TokenIssuer issuer(String secret, String issuer, String serviceId, String role,
                                    String audience) {
        SecretKey key = JwtSecuritySupport.secretKey(secret);
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        return new TokenIssuer(encoder, issuer, serviceId, role, audience, DEFAULT_LIFETIME, Clock.systemUTC());
    }

    public static final class TokenIssuer {
        private final JwtEncoder encoder;
        private final String issuer;
        private final String serviceId;
        private final String role;
        private final String audience;
        private final Duration lifetime;
        private final Clock clock;

        TokenIssuer(JwtEncoder encoder, String issuer, String serviceId, String role, String audience,
                    Duration lifetime, Clock clock) {
            this.encoder = encoder;
            this.issuer = issuer;
            this.serviceId = serviceId;
            this.role = role;
            this.audience = audience;
            this.lifetime = lifetime;
            this.clock = clock;
        }

        public String issue() {
            Instant now = clock.instant();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(serviceId)
                    .audience(List.of(audience))
                    .issuedAt(now)
                    .expiresAt(now.plus(lifetime))
                    .claim("roles", List.of(role))
                    .claim("token_type", "service")
                    .build();
            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        }
    }
}
