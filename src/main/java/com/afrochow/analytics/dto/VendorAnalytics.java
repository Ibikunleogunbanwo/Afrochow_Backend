package com.afrochow.analytics.dto;

import java.math.BigDecimal;

public record VendorAnalytics(
        Long vendorId,
        long totalOrders,
        long deliveredOrders,
        BigDecimal totalRevenue,
        BigDecimal averageOrderValue
) {}
