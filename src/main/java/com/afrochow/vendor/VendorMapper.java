package com.afrochow.vendor;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.common.enums.VendorStatus;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import com.afrochow.vendor.dto.VendorProfileUpdateRequestDto;
import com.afrochow.vendor.model.VendorProfile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapper for VendorProfile entity and DTOs
 * Centralized mapping logic to eliminate code duplication
 */
@Component
public class VendorMapper {

    /**
     * Convert VendorProfile entity to VendorProfileResponseDto
     */
    public VendorProfileResponseDto toResponseDto(VendorProfile profile) {
        if (profile == null) return null;

        // Convert entity operating hours to DTO
        Map<String, VendorProfileResponseDto.OperatingHoursDto> weeklySchedule =
                convertToDtoOperatingHours(profile.getOperatingHours());

        return VendorProfileResponseDto.builder()
                .publicUserId(profile.getPublicVendorId())
                .restaurantName(profile.getRestaurantName())
                .description(profile.getDescription())
                .storeCategory(profile.getStoreCategory())
                .logoUrl(profile.getLogoUrl())
                .bannerUrl(profile.getBannerUrl())
                .stripeAccountId(profile.getStripeAccountId())
                .stripeOnboardingComplete(profile.getStripeOnboardingComplete())
                // Status (new state machine)
                .vendorStatus(profile.getVendorStatus())
                .vendorStatusLabel(resolveStatusLabel(profile.getVendorStatus()))
                .canReceiveOrders(profile.canReceiveOrders())
                .isProvisional(profile.isProvisional())
                // Deprecated booleans (kept for backward compat)
                .isVerified(profile.getIsVerified())
                .isActive(profile.getIsActive())
                .verifiedAt(profile.getVerifiedAt())

                // Food handling certificate
                .foodHandlingCertUrl(profile.getFoodHandlingCertUrl())
                .foodHandlingCertNumber(profile.getFoodHandlingCertNumber())
                .foodHandlingCertIssuingBody(profile.getFoodHandlingCertIssuingBody())
                .foodHandlingCertExpiry(profile.getFoodHandlingCertExpiry())
                .certExpired(profile.isCertExpired())
                .certVerifiedAt(profile.getCertVerifiedAt())

                // Operating hours
                .weeklySchedule(weeklySchedule)
                .todayHoursFormatted(profile.getTodayHoursFormatted())
                .isOpenNow(profile.isOpenNow())

                // Service options
                .offersDelivery(profile.getOffersDelivery())
                .offersPickup(profile.getOffersPickup())
                .preparationTime(profile.getPreparationTime())

                // Delivery settings
                .deliveryFee(profile.getDeliveryFee())
                .minimumOrderAmount(profile.getMinimumOrderAmount())
                .estimatedDeliveryMinutes(profile.getEstimatedDeliveryMinutes())
                .maxDeliveryDistanceKm(profile.getMaxDeliveryDistanceKm())

                // Address
                .address(toAddressResponseDto(profile.getAddress()))

                // Statistics
                .totalOrdersCompleted(profile.getTotalOrdersCompleted())
                .totalRevenue(profile.getTotalRevenue())
                .averageRating(profile.getAverageRating())
                .reviewCount(profile.getReviewCount())

                // Timestamps
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    /**
     * Convert Address entity to AddressResponseDto
     */
    public AddressResponseDto toAddressResponseDto(Address address) {
        if (address == null) return null;

        return AddressResponseDto.builder()
                .publicAddressId(address.getPublicAddressId())
                .addressLine(address.getAddressLine())
                .city(address.getCity())
                .province(address.getProvince())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .formattedAddress(address.getFormattedAddress())
                .defaultAddress(address.getDefaultAddress())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }

    /**
     * Convert entity operating hours to DTO operating hours
     */
    public Map<String, VendorProfileResponseDto.OperatingHoursDto> convertToDtoOperatingHours(
            Map<String, VendorProfile.DayHours> entityHours) {

        if (entityHours == null || entityHours.isEmpty()) return null;

        Map<String, VendorProfileResponseDto.OperatingHoursDto> dtoHours = new HashMap<>();

        entityHours.forEach((day, hours) -> {
            VendorProfileResponseDto.OperatingHoursDto dto =
                    VendorProfileResponseDto.OperatingHoursDto.builder()
                            .isOpen(hours.getIsOpen())
                            .openTime(hours.getOpenTime())
                            .closeTime(hours.getCloseTime())
                            .build();
            dtoHours.put(day, dto);
        });

        return dtoHours;
    }

    /**
     * Convert DTO operating hours to entity operating hours
     */
    public Map<String, VendorProfile.DayHours> convertToEntityOperatingHours(
            Map<String, VendorProfileUpdateRequestDto.OperatingHoursDto> dtoHours) {

        if (dtoHours == null) return null;

        Map<String, VendorProfile.DayHours> entityHours = new HashMap<>();

        dtoHours.forEach((day, dto) -> {
            VendorProfile.DayHours hours = new VendorProfile.DayHours();
            hours.setIsOpen(dto.getIsOpen());
            hours.setOpenTime(dto.getOpenTime());
            hours.setCloseTime(dto.getCloseTime());
            entityHours.put(day, hours);
        });

        return entityHours;
    }

    /**
     * Returns a human-readable status label for display in the vendor dashboard.
     */
    private String resolveStatusLabel(VendorStatus status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case PENDING_PROFILE  -> "Complete Your Profile";
            case PENDING_REVIEW   -> "Under Review";
            case PROVISIONAL      -> "Provisionally Active — Upload Food Handling Certificate";
            case VERIFIED         -> "Verified";
            case SUSPENDED        -> "Suspended";
            case REJECTED         -> "Application Rejected";
        };
    }
}
