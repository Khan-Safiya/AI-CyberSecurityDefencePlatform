package com.cybersim.apigatewayservice.security;

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
public class GatewaySecurityConfiguration {
    @Bean
    JwtDecoder gatewayJwtDecoder(@Value("${gateway.jwt.secret}") String activeSecret,
                                 @Value("${gateway.jwt.previous-secret:}") String previousSecret,
                                 @Value("${gateway.jwt.issuer}") String issuer) {
        return JwtSecuritySupport.rotatingDecoder(activeSecret, previousSecret, issuer);
    }

    @Bean
    SecurityFilterChain gatewaySecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/actuator/health", "/actuator/info", "/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("ADMIN", "SIMULATION_OPERATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyRole("ADMIN", "SIMULATION_OPERATOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyRole("ADMIN", "SIMULATION_OPERATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole("ADMIN", "SIMULATION_OPERATOR")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "SIMULATION_OPERATOR", "AUDITOR")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(JwtSecuritySupport.authenticationConverter())))
                .build();
    }
}
