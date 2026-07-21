package com.cybersim.identityservice.security;

import com.cybersim.identityservice.controller.AuthController.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final UserAccountService userAccountService;
    private final String issuer;
    private final String activeKeyId;
    private final Duration lifetime;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, UserAccountService userAccountService,
                           @Value("${identity.jwt.issuer}") String issuer,
                           @Value("${identity.jwt.active-key-id}") String activeKeyId,
                           @Value("${identity.jwt.access-token-lifetime}") Duration lifetime) {
        this.encoder = encoder;
        this.userAccountService = userAccountService;
        this.issuer = issuer;
        this.activeKeyId = activeKeyId;
        this.lifetime = lifetime;
        this.clock = Clock.systemUTC();
    }

    public TokenResponse issue(Authentication authentication) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(lifetime);
        List<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_")).map(authority -> authority.substring(5)).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(issuedAt).expiresAt(expiresAt)
                .subject(authentication.getName())
                .claim("userId", userAccountService.idFor(authentication.getName()).toString())
                .claim("roles", roles).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(activeKeyId).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse("Bearer", token, expiresAt, roles);
    }
}
