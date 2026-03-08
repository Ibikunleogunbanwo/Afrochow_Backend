package com.afrochow.vendor.dto;
import com.afrochow.address.dto.AddressResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorProfileResponseDto {

    // Basic Information
    private String publicUserId;
    private String restaurantName;
    private String description;
    private String cuisineType;
    private String logoUrl;
    private String bannerUrl;

    // Verification Status
    private Boolean isVerified;
    private Boolean isActive;
    private LocalDateTime verifiedAt;


    private Map<String, OperatingHoursDto> weeklySchedule;


    private String todayHoursFormatted;
    private Boolean isOpenNow;

    // Service Options
    private Boolean offersDelivery;
    private Boolean offersPickup;
    private Integer preparationTime;

    // Delivery Settings
    private BigDecimal deliveryFee;
    private BigDecimal minimumOrderAmount;
    private Integer estimatedDeliveryMinutes;
    private BigDecimal maxDeliveryDistanceKm;

    // Location
    private AddressResponseDto address;

    // Statistics
    private Integer totalOrdersCompleted;
    private BigDecimal totalRevenue;
    private Double averageRating;
    private Integer reviewCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Inner class for operating hours
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperatingHoursDto {
        private Boolean isOpen;
        private String openTime;
        private String closeTime;
    }
}