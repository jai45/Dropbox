package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@Tag(name = "Health", description = "Service health check")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @Operation(
            summary = "Health check",
            description = "Returns 200 OK with server status and current timestamp",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Service is healthy")
            }
    )
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        log.info("Health check requested at {}", Instant.now());
        Map<String, Object> response = Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
        log.debug("Health check response: {}", response);
        return ResponseEntity.ok(response);
    }
}
