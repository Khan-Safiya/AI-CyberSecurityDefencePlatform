package com.cybersim.simulationorchestratorservice.security;

import com.cybersim.shared.security.JwtSecuritySupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SimulationSecurityConfiguration {
    @Bean
    JwtDecoder simulationJwtDecoder(@Value("${security.jwt.secret}") String userSecret,
                                    @Value("${security.jwt.previous-secret:}") String previousUserSecret,
                                    @Value("${security.jwt.issuer}") String userIssuer,
                                    @Value("${service-jwt.secret}") String serviceSecret,
                                    @Value("${service-jwt.issuer}") String serviceIssuer) {
        JwtDecoder userDecoder = JwtSecuritySupport.rotatingDecoder(userSecret, previousUserSecret, userIssuer);
        JwtDecoder serviceDecoder = JwtSecuritySupport.decoder(JwtSecuritySupport.secretKey(serviceSecret),
                serviceIssuer, "simulation-orchestrator-service");
        return token -> {
            try {
                return serviceDecoder.decode(token);
            } catch (JwtException ignored) {
                return userDecoder.decode(token);
            }
        };
    }

    @Bean
    SecurityFilterChain simulationSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/simulations/*/rounds/*/advance")
                        .hasRole("SERVICE_ORCHESTRATOR_CONTROL")
                        .requestMatchers(HttpMethod.POST, "/simulations/*/rounds/*/red-team-complete")
                        .hasRole("SERVICE_RED_TEAM")
                        .requestMatchers(HttpMethod.POST, "/simulations/*/rounds/*/detection-complete")
                        .hasRole("SERVICE_DETECTION")
                        .requestMatchers(HttpMethod.POST, "/simulations/*/rounds/*/blue-team-complete")
                        .hasRole("SERVICE_REMEDIATION")
                        .requestMatchers(HttpMethod.POST, "/simulations/*/rounds/*/verification-complete")
                        .hasRole("SERVICE_VERIFICATION")
                        .requestMatchers(HttpMethod.POST, "/simulations/*/rounds/*/complete")
                        .hasRole("SERVICE_SCORING")
                        .requestMatchers(HttpMethod.GET, "/simulations/**", "/integration/assessments/**", "/scenarios/**")
                        .hasAnyRole("ADMIN", "SIMULATION_OPERATOR", "AUDITOR", "SERVICE_RED_TEAM", "SERVICE_SCORING")
                        .requestMatchers(HttpMethod.POST, "/simulations", "/simulations/*/stop",
                                "/integration/targets/*/start-assessment")
                        .hasAnyRole("ADMIN", "SIMULATION_OPERATOR")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(JwtSecuritySupport.authenticationConverter())))
                .build();
    }

}
