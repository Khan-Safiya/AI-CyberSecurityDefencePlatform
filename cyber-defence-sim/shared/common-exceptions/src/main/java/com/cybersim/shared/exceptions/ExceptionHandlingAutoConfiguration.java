package com.cybersim.shared.exceptions;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ExceptionHandlingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    GlobalApiExceptionHandler globalApiExceptionHandler() {
        return new GlobalApiExceptionHandler();
    }
}
