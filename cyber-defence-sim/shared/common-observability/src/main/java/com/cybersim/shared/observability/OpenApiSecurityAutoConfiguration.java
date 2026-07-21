package com.cybersim.shared.observability;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiSecurityAutoConfiguration {
    public static final String USER_BEARER_AUTH = "userBearerAuth";
    public static final String SERVICE_BEARER_AUTH = "serviceBearerAuth";

    @Bean
    @ConditionalOnMissingBean
    OpenAPI cyberSimOpenApiSecurity() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cyber Defence Simulation Platform API")
                        .version("0.1.0")
                        .description("Generated API metadata for the local cyber defence simulation platform."))
                .components(new Components()
                        .addSecuritySchemes(USER_BEARER_AUTH, bearerJwtScheme(
                                "User JWT issued by identity-service. Used for platform users and RBAC."))
                        .addSecuritySchemes(SERVICE_BEARER_AUTH, bearerJwtScheme(
                                "Short-lived audience-restricted service JWT for internal operations.")));
    }

    private SecurityScheme bearerJwtScheme(String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(description);
    }
}
