package com.afrochow.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for popular cuisine statistics with sample vendors and products
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularCuisineDto {

    /**
     * Cuisine type name
     */
    private String cuisineType;

    /**
     * Number of vendors offering this cuisine
     */
    private Long vendorCount;

    /**
     * Total number of orders for this cuisine across all vendors
     */
    private Long totalOrders;

    /**
     * Average rating across all vendors for this cuisine
     */
    private Double averageRating;

    /**
     * Sample top vendors for this cuisine (max 3)
     */
    private List<VendorSummary> sampleVendors;

    /**
     * Sample popular products for this cuisine (max 6)
     */
    private List<ProductSummary> sampleProducts;

    /**
     * Representative image URL for the cuisine (from top vendor or product)
     */
    private String imageUrl;

    /**
     * Summary info for a vendor
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorSummary {
        private String publicVendorId;
        private String restaurantName;
        private String logoUrl;
        private Double rating;
        private Integer totalOrders;
        private Boolean isActive;
    }

    /**
     * Summary info for a product
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSummary {
        private String publicProductId;
        private String productName;
        private String imageUrl;
        private Double price;
        private Double rating;
        private Boolean isAvailable;
        private String vendorName;
    }
}
