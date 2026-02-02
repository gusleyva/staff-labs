package com.stafflabs.controller;

import com.stafflabs.service.ExternalService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
@Slf4j
public class ExternalController {

    private final ExternalService externalService;

    /**
     * External service simulation - introduces jitter and random failures
     */
    @GetMapping
    @Timed(value = "api.external.call", percentiles = { 0.5, 0.95, 0.99 })
    public ResponseEntity<Map<String, Object>> callExternal() {
        log.info("External service call initiated");

        try {
            String response = externalService.callExternalService();

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("response", response);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("External service call failed", e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());

            return ResponseEntity.status(503).body(error);
        }
    }
}
