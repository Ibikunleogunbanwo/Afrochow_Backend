package com.afrochow.analytics.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsResponseDto {

    private Integer totalOrders;
    private Integer completedOrders;
    private Integer pendingOrders;
    private Integer cancelledOrders;

    private BigDecimal totalRevenue;
    private BigDecimal averageOrderValue;

    private Integer totalCustomers;
    private Integer totalVendors;
    private Integer totalProducts;

    private Double averageRating;
    private Integer totalReviews;
}