package com.stafflabs.controller;

import com.stafflabs.service.CpuService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cpu")
@RequiredArgsConstructor
@Slf4j
public class CpuController {

    private final CpuService cpuService;

    /**
     * CPU saturation endpoint - pegs a CPU core for specified duration
     */
    @GetMapping
    @Timed(value = "api.cpu.burn", percentiles = { 0.5, 0.95, 0.99 })
    public ResponseEntity<Map<String, Object>> burnCpu(
            @RequestParam(defaultValue = "100") int ms) {

        log.info("CPU burn request for {} ms", ms);

        // Validate input
        if (ms < 0 || ms > 10000) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Duration must be between 0 and 10000 ms"));
        }

        long result = cpuService.burnCpu(ms);

        Map<String, Object> response = new HashMap<>();
        response.put("requestedDuration", ms);
        response.put("result", result);
        response.put("message", "CPU burn completed");

        return ResponseEntity.ok(response);
    }
}
