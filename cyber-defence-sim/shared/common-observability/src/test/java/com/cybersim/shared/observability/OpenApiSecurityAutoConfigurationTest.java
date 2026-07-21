package com.cybersim.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenApiSecurityAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenApiSecurityAutoConfiguration.class));

    @Test
    void exposesUserAndServiceBearerJwtSchemes() {
        contextRunner.run(context -> {
            OpenAPI openApi = context.getBean(OpenAPI.class);

            assertThat(openApi.getComponents().getSecuritySchemes())
                    .containsKeys(
                            OpenApiSecurityAutoConfiguration.USER_BEARER_AUTH,
                            OpenApiSecurityAutoConfiguration.SERVICE_BEARER_AUTH);

            SecurityScheme userScheme = openApi.getComponents().getSecuritySchemes()
                    .get(OpenApiSecurityAutoConfiguration.USER_BEARER_AUTH);
            assertThat(userScheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
            assertThat(userScheme.getScheme()).isEqualTo("bearer");
            assertThat(userScheme.getBearerFormat()).isEqualTo("JWT");

            SecurityScheme serviceScheme = openApi.getComponents().getSecuritySchemes()
                    .get(OpenApiSecurityAutoConfiguration.SERVICE_BEARER_AUTH);
            assertThat(serviceScheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
            assertThat(serviceScheme.getScheme()).isEqualTo("bearer");
            assertThat(serviceScheme.getBearerFormat()).isEqualTo("JWT");
        });
    }
}
