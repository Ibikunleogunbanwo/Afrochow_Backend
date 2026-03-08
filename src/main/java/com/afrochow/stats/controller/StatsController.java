package com.afrochow.stats.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.stats.dto.PlatformStatsDto;
import com.afrochow.stats.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint for platform statistics
 * Used for displaying stats on homepage, marketing pages, etc.
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "Platform statistics APIs")
public class StatsController {

    private final StatsService statsService;

    /**
     * Get platform-wide statistics (public endpoint)
     * Cached for performance - refreshes every 5 minutes
     */
    @GetMapping
    @Operation(
            summary = "Get platform statistics",
            description = "Get platform-wide statistics including total vendors, customers, orders, and ratings. " +
                         "This is a public endpoint cached for 5 minutes for optimal performance."
    )
    public ResponseEntity<ApiResponse<PlatformStatsDto>> getPlatformStats() {
        PlatformStatsDto stats = statsService.getPlatformStats();
        return ResponseEntity.ok(ApiResponse.success("Platform statistics retrieved successfully", stats));
    }
}
