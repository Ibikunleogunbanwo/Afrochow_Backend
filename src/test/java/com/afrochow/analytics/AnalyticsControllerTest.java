package com.afrochow.analytics;
import com.afrochow.analytics.controller.AnalyticsController;
import com.afrochow.analytics.service.AnalyticsService;

import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for AnalyticsController.
 *
 * Vendor/customer endpoints take a plain {@code Authentication} parameter
 * (covered via {@code authenticatedAs}). Admin endpoints are guarded by
 * {@code @deptAccess.can('REPORTS')}, not exercised in this slice (see
 * ControllerSliceTest javadoc).
 */
@ControllerSliceTest(AnalyticsController.class)
class AnalyticsControllerTest extends AbstractControllerTest {

    @MockitoBean private AnalyticsService analyticsService;

    private static final String VENDOR_USERNAME = "vendor-user";
    private static final String CUSTOMER_USERNAME = "customer-user";

    @Test
    void getVendorAnalytics_returns200() throws Exception {
        AnalyticsService.VendorAnalyticsDto analytics = AnalyticsService.VendorAnalyticsDto.builder()
                .totalOrders(50L)
                .totalRevenue(new BigDecimal("2500.00"))
                .averageRating(4.5)
                .build();
        when(analyticsService.getVendorAnalytics(VENDOR_USERNAME)).thenReturn(analytics);

        mockMvc.perform(get("/analytics/vendor")
                        .with(authenticatedAs(VENDOR_USERNAME, "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(50));
    }

    @Test
    void getVendorSalesReport_returns200() throws Exception {
        AnalyticsService.VendorSalesReport report = AnalyticsService.VendorSalesReport.builder()
                .totalOrders(20L)
                .deliveredOrders(18L)
                .totalRevenue(new BigDecimal("1000.00"))
                .averageOrderValue(new BigDecimal("55.56"))
                .build();
        when(analyticsService.getVendorSalesReport(eq(VENDOR_USERNAME), any(), any()))
                .thenReturn(report);

        mockMvc.perform(get("/analytics/vendor/sales-report")
                        .with(authenticatedAs(VENDOR_USERNAME, "VENDOR"))
                        .param("startDate", "2026-01-01T00:00:00")
                        .param("endDate", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveredOrders").value(18));
    }

    @Test
    void getVendorSalesReport_missingDates_returns400() throws Exception {
        mockMvc.perform(get("/analytics/vendor/sales-report")
                        .with(authenticatedAs(VENDOR_USERNAME, "VENDOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getVendorPopularProducts_returns200() throws Exception {
        AnalyticsService.PopularProduct product = AnalyticsService.PopularProduct.builder()
                .productPublicId("prod-1")
                .productName("Jollof Rice")
                .orderCount(30)
                .build();
        when(analyticsService.getVendorPopularProducts(VENDOR_USERNAME)).thenReturn(List.of(product));

        mockMvc.perform(get("/analytics/vendor/popular-products")
                        .with(authenticatedAs(VENDOR_USERNAME, "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productName").value("Jollof Rice"));
    }

    @Test
    void getCustomerAnalytics_returns200() throws Exception {
        AnalyticsService.CustomerAnalytics analytics = AnalyticsService.CustomerAnalytics.builder()
                .totalOrders(10L)
                .totalSpent(new BigDecimal("300.00"))
                .build();
        when(analyticsService.getCustomerAnalytics(CUSTOMER_USERNAME)).thenReturn(analytics);

        mockMvc.perform(get("/analytics/customer")
                        .with(authenticatedAs(CUSTOMER_USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(10));
    }

    @Test
    void getCustomerOrderHistory_returns200() throws Exception {
        AnalyticsService.CustomerOrderHistory history = AnalyticsService.CustomerOrderHistory.builder()
                .totalOrders(10L)
                .recentOrders(List.of())
                .build();
        when(analyticsService.getCustomerOrderHistory(CUSTOMER_USERNAME)).thenReturn(history);

        mockMvc.perform(get("/analytics/customer/order-history")
                        .with(authenticatedAs(CUSTOMER_USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(10));
    }

    @Test
    void getAdminAnalytics_returns200() throws Exception {
        AnalyticsService.AdminAnalytics analytics = AnalyticsService.AdminAnalytics.builder()
                .totalUsers(1000L)
                .totalOrders(5000L)
                .totalRevenue(new BigDecimal("250000.00"))
                .build();
        when(analyticsService.getAdminAnalytics(any(), any())).thenReturn(analytics);

        mockMvc.perform(get("/analytics/admin/platform")
                        .with(authenticatedAs("admin-1", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(1000));
    }

    @Test
    void getAdminAnalytics_withDateRange_returns200() throws Exception {
        AnalyticsService.AdminAnalytics analytics = AnalyticsService.AdminAnalytics.builder()
                .totalUsers(1000L)
                .filterStartDate(LocalDateTime.of(2026, 1, 1, 0, 0))
                .filterEndDate(LocalDateTime.of(2026, 1, 31, 23, 59))
                .build();
        when(analyticsService.getAdminAnalytics(any(), any())).thenReturn(analytics);

        mockMvc.perform(get("/analytics/admin/platform")
                        .with(authenticatedAs("admin-1", "ADMIN"))
                        .param("startDate", "2026-01-01T00:00:00")
                        .param("endDate", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(1000));
    }

    @Test
    void getPlatformTrends_returns200() throws Exception {
        AnalyticsService.PlatformTrends trends = AnalyticsService.PlatformTrends.builder()
                .ordersLast7Days(100L)
                .ordersLast30Days(400L)
                .revenueLast7Days(new BigDecimal("5000.00"))
                .revenueLast30Days(new BigDecimal("20000.00"))
                .build();
        when(analyticsService.getPlatformTrends(any(), any())).thenReturn(trends);

        mockMvc.perform(get("/analytics/admin/trends")
                        .with(authenticatedAs("admin-1", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ordersLast7Days").value(100));
    }
}
