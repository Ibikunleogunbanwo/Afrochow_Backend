package com.afrochow.vendor.dto;

import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.common.enums.VendorStatus;
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
    private String storeCategory;
    private String logoUrl;
    private String bannerUrl;

    // Stripe Connect
    private String stripeAccountId;
    private Boolean stripeOnboardingComplete;

    // ── Status (new state machine) ──
    private VendorStatus vendorStatus;

    /**
     * Human-readable label for the vendor's current status, suitable for display
     * in the vendor dashboard (e.g. "Pending Review", "Provisionally Active").
     */
    private String vendorStatusLabel;

    /**
     * Whether the vendor can currently receive orders
     * (true for PROVISIONAL and VERIFIED with operating days + active products).
     */
    private Boolean canReceiveOrders;

    /**
     * True if the vendor is live but food handling cert is not yet verified.
     * When true, a daily order cap applies.
     */
    private Boolean isProvisional;

    // ── Deprecated booleans (kept for backward compatibility) ──
    /** @deprecated Use vendorStatus instead */
    @Deprecated
    private Boolean isVerified;
    /** @deprecated Use vendorStatus instead */
    @Deprecated
    private Boolean isActive;

    private LocalDateTime verifiedAt;

    // ── Food Handling Certificate ──
    private String foodHandlingCertUrl;
    private String foodHandlingCertNumber;
    private String foodHandlingCertIssuingBody;
    private LocalDateTime foodHandlingCertExpiry;
    private Boolean certExpired;
    private LocalDateTime certVerifiedAt;

    // ── Operating Hours ──
    private Map<String, OperatingHoursDto> weeklySchedule;
    private String todayHoursFormatted;
    private Boolean isOpenNow;

    // ── Service Options ──
    private Boolean offersDelivery;
    private Boolean offersPickup;
    private Integer preparationTime;

    // ── Delivery Settings ──
    private BigDecimal deliveryFee;
    private BigDecimal minimumOrderAmount;
    private Integer estimatedDeliveryMinutes;
    private BigDecimal maxDeliveryDistanceKm;

    // ── Location ──
    private AddressResponseDto address;

    // ── Statistics ──
    private Integer totalOrdersCompleted;
    private BigDecimal totalRevenue;
    private Double averageRating;
    private Integer reviewCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Inner class for operating hours ──
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
