package com.stafflabs.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalService {

    private final MeterRegistry meterRegistry;
    private final RestClient restClient;
    private final com.stafflabs.config.MockConfig mockConfig;

    /**
     * Simulates calling an external service with jitter and random failures
     * Uses virtual threads if available (Java 21)
     */
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "externalService", fallbackMethod = "fallback")
    @io.github.resilience4j.retry.annotation.Retry(name = "externalService")
    @io.github.resilience4j.bulkhead.annotation.Bulkhead(name = "externalService", type = io.github.resilience4j.bulkhead.annotation.Bulkhead.Type.SEMAPHORE)
    @io.github.resilience4j.ratelimiter.annotation.RateLimiter(name = "externalService")
    public String callExternalService() {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            int jitterMs = mockConfig.getDelayMs();
            double failureRate = mockConfig.getFailureRate();

            // Simulate network jitter
            if (jitterMs > 0) {
                int actualJitter = ThreadLocalRandom.current().nextInt(0, jitterMs * 2);
                Thread.sleep(actualJitter);
            }

            // Simulate random failures
            Random rand = ThreadLocalRandom.current();
            if (rand.nextDouble() < failureRate) {
                meterRegistry.counter("external.service.failures").increment();
                log.warn("External service call failed (simulated)");
                throw new RuntimeException("External service unavailable (simulated failure)");
            }

            String response = "External service response: " + System.currentTimeMillis();

            meterRegistry.counter("external.service.success").increment();
            sample.stop(meterRegistry.timer("external.service.duration"));

            log.debug("External service call succeeded");
            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            meterRegistry.counter("external.service.interrupted").increment();
            throw new RuntimeException("External service call interrupted", e);
        }
    }

    public String fallback(Throwable t) {
        log.error("External service fallback triggered. Reason: {}", t.getMessage());
        meterRegistry.counter("external.service.fallback").increment();
        return "Graceful Degradation: Cached Response (Service Unavailable)";
    }
}
