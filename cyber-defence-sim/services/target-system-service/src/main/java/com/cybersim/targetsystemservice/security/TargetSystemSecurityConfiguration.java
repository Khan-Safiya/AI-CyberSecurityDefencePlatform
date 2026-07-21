package com.cybersim.targetsystemservice.security;

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
public class TargetSystemSecurityConfiguration {
    @Bean
    JwtDecoder targetSystemJwtDecoder(@Value("${service-jwt.secret}") String secret,
                                      @Value("${service-jwt.issuer}") String issuer) {
        return JwtSecuritySupport.decoder(JwtSecuritySupport.secretKey(secret), issuer, "target-system-service");
    }

    @Bean
    SecurityFilterChain targetSystemSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/demo/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/patches/*/rollback")
                        .hasRole("SERVICE_REMEDIATION")
                        .requestMatchers(HttpMethod.GET, "/internal/patches/status")
                        .hasAnyRole("SERVICE_RED_TEAM", "SERVICE_REMEDIATION", "SERVICE_VERIFICATION")
                        .requestMatchers(HttpMethod.POST, "/internal/patches/*").hasRole("SERVICE_REMEDIATION")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(JwtSecuritySupport.authenticationConverter())))
                .build();
    }
}
