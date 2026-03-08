package com.afrochow.product.dto;
import com.afrochow.common.enums.Province;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDto {

    private String publicProductId;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private Boolean available;
    private Integer preparationTimeMinutes;

    private Integer calories;
    private Boolean isVegetarian;
    private Boolean isVegan;
    private Boolean isGlutenFree;
    private Boolean isSpicy;

    private String vendorPublicId;
    private String restaurantName;
    private Long categoryId;
    private String categoryName;

    // Vendor Address Information
    private String vendorAddressLine;
    private String vendorCity;
    private Province vendorProvince;
    private String vendorPostalCode;
    private String vendorCountry;
    private String vendorFormattedAddress;

    private Double averageRating;
    private Integer reviewCount;
    private Integer totalOrders;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}