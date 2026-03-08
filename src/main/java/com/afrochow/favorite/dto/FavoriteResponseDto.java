package com.afrochow.favorite.dto;

import com.afrochow.common.enums.FavoriteType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for favorite data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteResponseDto {

    private Long favoriteId;
    private FavoriteType favoriteType;
    private LocalDateTime createdAt;

    // Vendor details (if favoriteType = VENDOR)
    private VendorBasicInfo vendor;

    // Product details (if favoriteType = PRODUCT)
    private ProductBasicInfo product;

    /**
     * Basic vendor info for favorites list
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorBasicInfo {
        private String publicVendorId;
        private String restaurantName;
        private String logoUrl;
        private String cuisine;
        private Double rating;
        private Boolean isActive;
    }

    /**
     * Basic product info for favorites list
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductBasicInfo {
        private String publicProductId;
        private String productName;
        private String imageUrl;
        private Double price;
        private Boolean isAvailable;
        private String vendorName;
        private String vendorPublicId;
    }
}
