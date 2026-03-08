package com.afrochow.vendor.dto;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.auth.dto.BaseRegistrationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorProfileRequestDto extends BaseRegistrationRequest {

    // ========== REQUIRED USERNAME (FOR BASE CLASS) ==========
    @Schema(description = "Username (optional - auto-generated if not provided)")
    private String username;

    @Override
    public String getUsername() {
        return this.username;
    }

    // ========== RESTAURANT INFORMATION ==========
    @NotBlank(message = "Restaurant name is required")
    @Size(max = 100, message = "Restaurant name must not exceed 100 characters")
    private String restaurantName;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 1000, message = "Description must be between 10 and 1000 characters")
    private String description;

    @NotBlank(message = "Cuisine type is required")
    @Size(max = 50, message = "Cuisine type must not exceed 50 characters")
    private String cuisineType;

    // ========== BRANDING & DOCUMENTS ==========
    @NotBlank(message = "Logo URL is required")
    private String logoUrl;

    @NotBlank(message = "Banner URL is required")
    private String bannerUrl;

    @Size(max = 255, message = "Business license URL must not exceed 255 characters")
    private String businessLicenseUrl;

    @Size(min = 5, max = 50, message = "Tax ID must be between 5 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9-]*$", message = "Tax ID can only contain letters, numbers, and hyphens")
    private String taxId;

    // ========== OPERATING HOURS (NEW STRUCTURE) ==========
    @NotNull(message = "Operating hours are required")
    @Valid
    @Schema(
            description = "Weekly operating hours schedule for each day of the week. Keys must be lowercase day names: Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday",
            example = """
            {
              "Monday": {"isOpen": true, "openTime": "09:00", "closeTime": "22:00"},
              "Tuesday": {"isOpen": true, "openTime": "09:00", "closeTime": "22:00"},
              "Wednesday": {"isOpen": true, "openTime": "09:00", "closeTime": "22:00"},
              "Thursday": {"isOpen": true, "openTime": "09:00", "closeTime": "22:00"},
              "Friday": {"isOpen": true, "openTime": "09:00", "closeTime": "23:00"},
              "Saturday": {"isOpen": true, "openTime": "10:00", "closeTime": "23:00"},
              "Sunday": {"isOpen": false, "openTime": "00:00", "closeTime": "00:00"}
            }
            """
    )
    private Map<String, DayHoursDto> operatingHours;

    // ========== SERVICE OPTIONS (NEW FIELDS) ==========
    @NotNull(message = "Delivery option must be specified")
    @Schema(description = "Whether the restaurant offers delivery service", example = "true")
    private Boolean offersDelivery;

    @NotNull(message = "Pickup option must be specified")
    @Schema(description = "Whether the restaurant offers pickup service", example = "true")
    private Boolean offersPickup;

    @NotNull(message = "Preparation time is required")
    @Min(value = 5, message = "Minimum preparation time is 5 minutes")
    @Max(value = 180, message = "Maximum preparation time is 180 minutes")
    @Schema(description = "Average time to prepare an order in minutes", example = "30")
    private Integer preparationTime;

    // ========== DELIVERY SETTINGS (CONDITIONAL) ==========
    @DecimalMin(value = "0.00", message = "Delivery fee must be at least 0")
    @Digits(integer = 8, fraction = 2, message = "Delivery fee must have at most 2 decimal places")
    @Schema(description = "Delivery fee (required if offersDelivery is true)", example = "5.99")
    private BigDecimal deliveryFee;

    @DecimalMin(value = "0.00", message = "Minimum order amount must be at least 0")
    @Digits(integer = 8, fraction = 2, message = "Minimum order amount must have at most 2 decimal places")
    @Schema(description = "Minimum order amount for delivery (required if offersDelivery is true)", example = "20.00")
    private BigDecimal minimumOrderAmount;

    @Min(value = 10, message = "Estimated delivery time must be at least 10 minutes")
    @Max(value = 180, message = "Estimated delivery time must be less than 180 minutes")
    @Schema(description = "Estimated delivery time in minutes (required if offersDelivery is true)", example = "45")
    private Integer estimatedDeliveryMinutes;

    @DecimalMin(value = "1.0", message = "Maximum delivery distance must be at least 1 km")
    @DecimalMax(value = "50.0", message = "Maximum delivery distance must be less than 50 km")
    @Digits(integer = 2, fraction = 1, message = "Maximum delivery distance must have at most 1 decimal place")
    @Schema(description = "Maximum delivery radius in kilometers (required if offersDelivery is true)", example = "10.5")
    private BigDecimal maxDeliveryDistanceKm;

    // ========== ADDRESS ==========
    @NotNull(message = "Address is required")
    @Valid
    private AddressRequestDto address;

    // ========== NESTED DTO FOR DAY HOURS ==========
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Operating hours for a specific day")
    public static class DayHoursDto {

        @NotNull(message = "isOpen must be specified")
        @Schema(description = "Whether the restaurant is open on this day", example = "true")
        private Boolean isOpen;

        @NotBlank(message = "Open time is required")
        @Pattern(regexp = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Invalid time format (must be HH:MM)")
        @Schema(description = "Opening time in HH:MM format (24-hour)", example = "09:00")
        private String openTime;

        @NotBlank(message = "Close time is required")
        @Pattern(regexp = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Invalid time format (must be HH:MM)")
        @Schema(description = "Closing time in HH:MM format (24-hour)", example = "22:00")
        private String closeTime;
    }

    // ========== CUSTOM VALIDATION METHODS ==========

    @AssertTrue(message = "At least one service option (delivery or pickup) must be enabled")
    public boolean isAtLeastOneServiceEnabled() {
        return (offersDelivery != null && offersDelivery) ||
                (offersPickup != null && offersPickup);
    }

    @AssertTrue(message = "All delivery settings are required when delivery is enabled")
    public boolean isDeliverySettingsValid() {
        if (offersDelivery != null && offersDelivery) {
            return deliveryFee != null &&
                    minimumOrderAmount != null &&
                    estimatedDeliveryMinutes != null &&
                    maxDeliveryDistanceKm != null;
        }
        return true;
    }

    @AssertTrue(message = "At least one day must be open")
    public boolean isAtLeastOneDayOpen() {
        if (operatingHours == null || operatingHours.isEmpty()) {
            return false;
        }

        return operatingHours.values().stream()
                .anyMatch(day -> day != null && Boolean.TRUE.equals(day.getIsOpen()));
    }
}