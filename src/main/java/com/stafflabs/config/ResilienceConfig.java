package com.stafflabs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Resilience4j toggle feature.
 * Allows enabling/disabling all resilience patterns (circuit breaker, retry,
 * bulkhead, rate limiter) at runtime.
 */
@Configuration
@ConfigurationProperties(prefix = "app.resilience")
@Data
public class ResilienceConfig {

    /**
     * Master toggle for all resilience patterns.
     * When false, all Resilience4j annotations are bypassed.
     */
    private boolean enabled = true;
}
