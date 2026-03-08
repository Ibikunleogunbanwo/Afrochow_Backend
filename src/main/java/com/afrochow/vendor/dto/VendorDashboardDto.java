package com.afrochow.vendor.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorDashboardDto {

    private Integer todayOrders;
    private Integer pendingOrders;
    private Integer preparingOrders;
    private BigDecimal todayRevenue;
    private BigDecimal weekRevenue;
    private BigDecimal monthRevenue;
    private Double averageRating;
    private Integer totalReviews;
    private Integer activeProducts;
    private Integer totalProducts;
}