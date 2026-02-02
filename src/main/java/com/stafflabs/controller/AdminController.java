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
}
