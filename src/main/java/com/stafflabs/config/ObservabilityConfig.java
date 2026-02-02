package com.stafflabs.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ObservabilityConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder
                .build();
    }
}
