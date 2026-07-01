package com.cybersim.shared.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ObservabilityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
