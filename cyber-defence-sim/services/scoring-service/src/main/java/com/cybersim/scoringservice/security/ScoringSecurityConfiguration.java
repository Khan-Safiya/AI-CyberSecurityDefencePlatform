package com.cybersim.scoringservice.security;

import com.cybersim.shared.security.JwtSecuritySupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ScoringSecurityConfiguration {
    @Bean
    JwtDecoder scoringServiceJwtDecoder(@Value("${service-jwt.secret}") String secret,
                                        @Value("${service-jwt.issuer}") String issuer) {
        return JwtSecuritySupport.decoder(JwtSecuritySupport.secretKey(secret), issuer, "scoring-service");
    }

    @Bean
    SecurityFilterChain scoringSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/score-events").hasRole("SERVICE_SCORE_PRODUCER")
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(JwtSecuritySupport.authenticationConverter())))
                .build();
    }
}
