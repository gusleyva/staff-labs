package com.stafflabs.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.external-service.jitter-ms:50}")
    private int jitterMs;

    @Value("${app.external-service.failure-rate:0.1}")
    private double failureRate;

    /**
     * Simulates calling an external service with jitter and random failures
     * Uses virtual threads if available (Java 21)
     */
    public String callExternalService() {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // Simulate network jitter
            if (jitterMs > 0) {
                int actualJitter = ThreadLocalRandom.current().nextInt(0, jitterMs * 2);
                Thread.sleep(actualJitter);
            }

            // Simulate random failures (10% by default)
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
}
