package com.afrochow.analytics.dto;

import java.math.BigDecimal;

public record AnalyticsSummaryDto(
        long totalVendors,
        long activeVendors,
        long totalOrders,
        long deliveredOrders,
        BigDecimal totalRevenue,
        BigDecimal averageOrderValue
) {}

