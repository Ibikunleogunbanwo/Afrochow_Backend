package com.afrochow.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for popular store category statistics with sample vendors and products.
 * Covers restaurants, grocery stores, farm produce, bakeries, and all other
 * store categories on the platform.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularCategoryDto {

    /** Store category name (e.g. "African Restaurant", "Farm Produce"). */
    private String storeCategory;

    /** Number of vendors offering this store category. */
    private Long vendorCount;

    /** Total number of orders across all vendors in this category. */
    private Long totalOrders;

    /** Average rating across all vendors in this category. */
    private Double averageRating;

    /** Sample top vendors for this category (max 3). */
    private List<VendorSummary> sampleVendors;

    /** Sample popular products for this category (max 6). */
    private List<ProductSummary> sampleProducts;

    /** Representative image URL for the category (from top vendor or product). */
    private String imageUrl;

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
