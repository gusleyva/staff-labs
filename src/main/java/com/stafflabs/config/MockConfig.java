package com.stafflabs.config;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MockConfig {
    // Atomic references for thread-safe dynamic updates
    private final AtomicReference<Double> failureRate = new AtomicReference<>(0.1);
    private final AtomicInteger delayMs = new AtomicInteger(50);

    public double getFailureRate() {
        return failureRate.get();
    }

    public void setFailureRate(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Failure rate must be between 0.0 and 1.0");
        }
        this.failureRate.set(rate);
    }

    public int getDelayMs() {
        return delayMs.get();
    }

    public void setDelayMs(int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("Delay must be non-negative");
        }
        this.delayMs.set(delay);
    }
}
