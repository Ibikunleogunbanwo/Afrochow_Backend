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
public class VendorSummaryResponseDto {

    private String publicUserId;
    private String restaurantName;
    private String cuisineType;
    private String logoUrl;
    private BigDecimal deliveryFee;
    private BigDecimal minimumOrderAmount;
    private Integer estimatedDeliveryMinutes;
    private Double averageRating;
    private Integer reviewCount;
    private Boolean isOpenNow;
    private Boolean isVerified;
}