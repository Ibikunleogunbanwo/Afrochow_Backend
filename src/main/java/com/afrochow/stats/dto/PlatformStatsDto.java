package com.afrochow.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for platform-wide statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformStatsDto {

    private Long totalVendors;
    private Long totalActiveVendors;
    private Long totalVerifiedVendors;

    private Long totalCustomers;
    private Long totalActiveCustomers;

    private Long totalProducts;
    private Long totalAvailableProducts;

    private Long totalOrders;
    private Long totalCompletedOrders;

    private Integer averageDeliveryTimeMinutes;

    private Long totalReviews;
    private Double averagePlatformRating;
}
