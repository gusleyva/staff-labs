package com.stafflabs.controller;

import com.stafflabs.service.OrderService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final OrderService orderService;
    private final com.stafflabs.config.MockConfig mockConfig;

    /**
     * Seed endpoint - high-speed batch insertion using JdbcTemplate
     */
    @PostMapping("/seed")
    @Timed(value = "admin.seed", percentiles = { 0.5, 0.95, 0.99 })
    public ResponseEntity<Map<String, Object>> seedData(
            @RequestParam(defaultValue = "10000") int count) {

        log.info("Seed request for {} orders", count);

        // Validate input
        if (count < 1 || count > 10_000_000) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Count must be between 1 and 10,000,000"));
        }

        long startTime = System.currentTimeMillis();
        long totalOrders = orderService.seedOrders(count);
        long duration = System.currentTimeMillis() - startTime;

        Map<String, Object> response = new HashMap<>();
        response.put("seeded", count);
        response.put("totalOrders", totalOrders);
        response.put("durationMs", duration);
        response.put("ordersPerSecond", count * 1000.0 / duration);

        return ResponseEntity.ok(response);
    }

    @PostMapping({ "/mock/configure", "/mock/config" })
    public ResponseEntity<Map<String, Object>> configureMock(
            @RequestBody(required = false) MockSettings settings,
            @RequestParam(required = false) Double failureRate,
            @RequestParam(required = false) Integer delayMs) {

        // Priority: RequestBody > RequestParam > CurrentValue
        Double finalFailureRate = (settings != null && settings.failureRate() != null) ? settings.failureRate()
                : failureRate;
        Integer finalDelayMs = (settings != null && settings.delayMs() != null) ? settings.delayMs() : delayMs;

        if (finalFailureRate != null) {
            mockConfig.setFailureRate(finalFailureRate);
        }
        if (finalDelayMs != null) {
            mockConfig.setDelayMs(finalDelayMs);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Mock settings updated",
                "currentSettings", Map.of(
                        "failureRate", mockConfig.getFailureRate(),
                        "delayMs", mockConfig.getDelayMs())));
    }

    public record MockSettings(Double failureRate, Integer delayMs) {
    }
}
