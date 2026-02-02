package com.stafflabs.controller;

import com.stafflabs.service.OrderService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db")
@RequiredArgsConstructor
@Slf4j
public class DatabaseController {

    private final OrderService orderService;

    /**
     * Slow query endpoint - GROUP BY on unindexed columns
     */
    @GetMapping("/search")
    @Timed(value = "api.db.search", percentiles = { 0.5, 0.95, 0.99 })
    public ResponseEntity<Map<String, Object>> searchOrders() {
        log.info("Executing slow database query");

        List<Object[]> results = orderService.getOrderStatistics();

        Map<String, Object> response = new HashMap<>();
        response.put("resultCount", results.size());
        response.put("message", "Query executed with GROUP BY on unindexed columns");
        response.put("sampleResults", results.stream().limit(10).toList());

        return ResponseEntity.ok(response);
    }
}
