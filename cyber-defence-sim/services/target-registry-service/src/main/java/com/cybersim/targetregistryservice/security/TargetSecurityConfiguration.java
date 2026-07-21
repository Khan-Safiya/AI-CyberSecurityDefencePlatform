package com.cybersim.targetregistryservice.security;

import com.cybersim.shared.security.JwtSecuritySupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class TargetSecurityConfiguration {
    @Bean
    JwtDecoder targetJwtDecoder(@Value("${security.jwt.secret}") String activeSecret,
                                @Value("${security.jwt.previous-secret:}") String previousSecret,
                                @Value("${security.jwt.issuer}") String issuer) {
        return JwtSecuritySupport.rotatingDecoder(activeSecret, previousSecret, issuer);
    }

    @Bean
    SecurityFilterChain targetSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/targets/**", "/integration/targets/**")
                        .hasAnyRole("ADMIN", "SIMULATION_OPERATOR", "AUDITOR")
                        .requestMatchers("/targets/**", "/integration/targets/**")
                        .hasAnyRole("ADMIN", "SIMULATION_OPERATOR")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(JwtSecuritySupport.authenticationConverter())))
                .build();
    }
}
