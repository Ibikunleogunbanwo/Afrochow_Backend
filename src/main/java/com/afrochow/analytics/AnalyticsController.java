package com.afrochow.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Analytics and reporting APIs")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    // ================= VENDOR =================

    @GetMapping("/vendor")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(
            summary = "Get vendor analytics",
            description = "Get comprehensive analytics for the authenticated vendor"
    )
    public ResponseEntity<AnalyticsService.VendorAnalytics> getVendorAnalytics(
            Authentication authentication) {

        return ResponseEntity.ok(
                analyticsService.getVendorAnalytics(authentication.getName())
        );
    }

    @GetMapping("/vendor/sales-report")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(
            summary = "Get vendor sales report",
            description = "Get vendor sales report for a specific date range"
    )
    public ResponseEntity<AnalyticsService.VendorSalesReport> getVendorSalesReport(
            Authentication authentication,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate) {

        return ResponseEntity.ok(
                analyticsService.getVendorSalesReport(
                        authentication.getName(),
                        startDate,
                        endDate
                )
        );
    }

    @GetMapping("/vendor/popular-products")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(
            summary = "Get popular products",
            description = "Get top 10 popular products for the authenticated vendor"
    )
    public ResponseEntity<List<AnalyticsService.PopularProduct>> getVendorPopularProducts(
            Authentication authentication) {

        return ResponseEntity.ok(
                analyticsService.getVendorPopularProducts(authentication.getName())
        );
    }

    // ================= CUSTOMER =================

    @GetMapping("/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get customer analytics",
            description = "Get analytics for the authenticated customer"
    )
    public ResponseEntity<AnalyticsService.CustomerAnalytics> getCustomerAnalytics(
            Authentication authentication) {

        return ResponseEntity.ok(
                analyticsService.getCustomerAnalytics(authentication.getName())
        );
    }

    @GetMapping("/customer/order-history")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get customer order history",
            description = "Get recent order history for the authenticated customer"
    )
    public ResponseEntity<AnalyticsService.CustomerOrderHistory> getCustomerOrderHistory(
            Authentication authentication) {

        return ResponseEntity.ok(
                analyticsService.getCustomerOrderHistory(authentication.getName())
        );
    }

    // ================= ADMIN =================

    @GetMapping("/admin/platform")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get platform analytics",
            description = "Get platform-wide analytics (admin only)"
    )
    public ResponseEntity<AnalyticsService.AdminAnalytics> getAdminAnalytics() {
        return ResponseEntity.ok(analyticsService.getAdminAnalytics());
    }

    @GetMapping("/admin/trends")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get platform trends",
            description = "Get platform sales trends (admin only)"
    )
    public ResponseEntity<AnalyticsService.PlatformTrends> getPlatformTrends() {
        return ResponseEntity.ok(analyticsService.getPlatformTrends());
    }
}
