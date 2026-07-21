package com.cybersim.notificationservice.security;

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
public class NotificationSecurityConfiguration {
    @Bean
    JwtDecoder notificationJwtDecoder(@Value("${service-jwt.secret}") String secret,
                                      @Value("${service-jwt.issuer}") String issuer) {
        return JwtSecuritySupport.decoder(JwtSecuritySupport.secretKey(secret), issuer, "notification-service");
    }

    @Bean
    SecurityFilterChain notificationSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/notifications").hasRole("SERVICE_NOTIFICATION")
                        .requestMatchers(HttpMethod.GET, "/webhooks/deliveries").hasRole("SERVICE_NOTIFICATION")
                        .requestMatchers(HttpMethod.POST, "/webhooks/deliveries").hasRole("SERVICE_NOTIFICATION")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(JwtSecuritySupport.authenticationConverter())))
                .build();
    }
}
