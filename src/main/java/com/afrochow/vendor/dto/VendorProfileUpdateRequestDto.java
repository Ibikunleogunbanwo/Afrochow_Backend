package com.afrochow.vendor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorProfileUpdateRequestDto {

    // Basic Information
    @Size(max = 100, message = "Restaurant name must not exceed 100 characters")
    private String restaurantName;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Size(max = 50, message = "Cuisine type must not exceed 50 characters")
    private String cuisineType;

    private String logoUrl;
    private String bannerUrl;

    // Business Verification (Admin-only fields - should be ignored for regular vendors)
    private String businessLicenseUrl;

    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    private String taxId;

    // Note: isVerified should typically only be updated by admins
    private Boolean isVerified;

    private Boolean isActive;

    // Operating Hours - Full Weekly Schedule
    @Valid
    private Map<String, OperatingHoursDto> operatingHours;

    // Service Options
    private Boolean offersDelivery;
    private Boolean offersPickup;

    @Min(value = 1, message = "Preparation time must be at least 1 minute")
    @Max(value = 180, message = "Preparation time must not exceed 180 minutes")
    private Integer preparationTime;

    // Delivery Settings
    @DecimalMin(value = "0.0", inclusive = true, message = "Delivery fee cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "Delivery fee must have at most 2 decimal places")
    private BigDecimal deliveryFee;

    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum order amount cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "Minimum order amount must have at most 2 decimal places")
    private BigDecimal minimumOrderAmount;

    @Min(value = 1, message = "Estimated delivery time must be at least 1 minute")
    @Max(value = 300, message = "Estimated delivery time must not exceed 300 minutes")
    private Integer estimatedDeliveryMinutes;

    @DecimalMin(value = "0.1", inclusive = true, message = "Max delivery distance must be at least 0.1 km")
    @DecimalMax(value = "100.0", inclusive = true, message = "Max delivery distance must not exceed 100 km")
    @Digits(integer = 3, fraction = 1, message = "Max delivery distance must have at most 1 decimal place")
    private BigDecimal maxDeliveryDistanceKm;

    // Inner class for operating hours
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperatingHoursDto {
        @NotNull(message = "Must specify if day is open")
        private Boolean isOpen;

        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
                message = "Open time must be in HH:mm format (e.g., 09:00)")
        private String openTime;

        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
                message = "Close time must be in HH:mm format (e.g., 22:00)")
        private String closeTime;
    }

    // Custom validation method (call this in service layer)
    public boolean hasAtLeastOneService() {
        return Boolean.TRUE.equals(offersDelivery) || Boolean.TRUE.equals(offersPickup);
    }

    public boolean hasAtLeastOneOpenDay() {
        if (operatingHours == null || operatingHours.isEmpty()) {
            return false;
        }
        return operatingHours.values().stream()
                .anyMatch(day -> day != null && Boolean.TRUE.equals(day.getIsOpen()));
    }
}