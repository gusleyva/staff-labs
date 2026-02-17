package com.stafflabs.controller;

import com.stafflabs.config.ResilienceConfig;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin controller for managing resilience features at runtime.
 */
@RestController
@RequestMapping("/admin/resilience")
@RequiredArgsConstructor
@Slf4j
public class ResilienceAdminController {

    private final ResilienceConfig resilienceConfig;
    private final MeterRegistry meterRegistry;

    /**
     * Get current resilience toggle status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", resilienceConfig.isEnabled());
        status.put("patterns", Map.of(
                "circuitBreaker", resilienceConfig.isEnabled(),
                "retry", resilienceConfig.isEnabled(),
                "bulkhead", resilienceConfig.isEnabled(),
                "rateLimiter", resilienceConfig.isEnabled()));

        log.debug("Resilience status queried: enabled={}", resilienceConfig.isEnabled());
        return ResponseEntity.ok(status);
    }

    /**
     * Toggle resilience features on or off at runtime.
     * 
     * @param enabled true to enable all resilience patterns, false to disable
     */
    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleResilience(@RequestParam boolean enabled) {
        boolean previousState = resilienceConfig.isEnabled();
        resilienceConfig.setEnabled(enabled);

        log.warn("Resilience toggle changed: {} -> {} (all patterns affected)",
                previousState, enabled);

        // Emit metric for toggle event
        meterRegistry.counter("resilience.toggle.changed",
                "from", String.valueOf(previousState),
                "to", String.valueOf(enabled)).increment();

        // Update gauge for current state
        meterRegistry.gauge("resilience.toggle.state", enabled ? 1 : 0);

        Map<String, Object> response = new HashMap<>();
        response.put("previousState", previousState);
        response.put("currentState", enabled);
        response.put("message", enabled ? "Resilience patterns ENABLED (circuit breaker, retry, bulkhead, rate limiter)"
                : "Resilience patterns DISABLED (all protections bypassed)");

        return ResponseEntity.ok(response);
    }

    /**
     * Enable resilience patterns.
     */
    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> enableResilience() {
        return toggleResilience(true);
    }

    /**
     * Disable resilience patterns.
     */
    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disableResilience() {
        return toggleResilience(false);
    }
}
