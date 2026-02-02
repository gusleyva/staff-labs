package com.stafflabs.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CpuService {

    private final MeterRegistry meterRegistry;

    /**
     * Pegs a CPU core for the specified duration using Fibonacci calculation
     * This simulates CPU-intensive work and demonstrates CPU saturation
     */
    public long burnCpu(int durationMs) {
        Timer.Sample sample = Timer.start(meterRegistry);

        log.info("Starting CPU burn for {} ms", durationMs);

        long startTime = System.currentTimeMillis();
        long endTime = startTime + durationMs;
        long result = 0;

        // Non-blocking busy loop with CPU-intensive calculation
        while (System.currentTimeMillis() < endTime) {
            // Calculate Fibonacci to actually use CPU cycles
            result = fibonacci(35); // Fibonacci(35) takes a noticeable amount of CPU
        }

        long actualDuration = System.currentTimeMillis() - startTime;

        sample.stop(meterRegistry.timer("cpu.burn.duration"));
        meterRegistry.counter("cpu.burn.count").increment();

        log.info("CPU burn completed. Requested: {} ms, Actual: {} ms", durationMs, actualDuration);

        return result;
    }

    /**
     * Recursive Fibonacci calculation (inefficient by design)
     * This ensures actual CPU work is being done
     */
    private long fibonacci(int n) {
        if (n <= 1)
            return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }
}
