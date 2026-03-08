package com.afrochow.auth;

import com.afrochow.common.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/public")
public class TestController {

    @GetMapping("/hello")
    public ResponseEntity<ApiResponse<String>> hello() {
        return ResponseEntity.ok(
                ApiResponse.<String>success("Request successful", "Hello World from Afrochow API!")
        );
    }

    /**
     * Enhanced health check with version info
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthStatus>> health() {
        HealthStatus healthData = new HealthStatus("UP", "afrochow-api", "1.0.0");
        return ResponseEntity.ok(
                ApiResponse.<HealthStatus>success("Service is healthy", healthData)
        );
    }

    /**
     * Health status DTO
     */
    @Data
    @AllArgsConstructor
    public static class HealthStatus {
        private String status;
        private String service;
        private String version;
    }
}