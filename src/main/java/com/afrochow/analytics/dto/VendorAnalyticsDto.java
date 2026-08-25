package com.afrochow.analytics.dto;

import java.math.BigDecimal;

public record VendorAnalyticsDto(
        Long vendorId,
        long totalOrders,
        long deliveredOrders,
        BigDecimal totalRevenue,
        BigDecimal averageOrderValue
) {}
