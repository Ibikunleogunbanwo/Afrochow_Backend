package com.afrochow.admin.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/vendors")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
@Tag(name = "Admin Vendor Management", description = "Admin APIs for managing vendor profiles")
public class AdminVendorManagementController {

    private final VendorProfileRepository vendorProfileRepository;

    // ========== VIEW VENDORS ==========

    @GetMapping
    @Operation(summary = "Get all vendors", description = "Get all vendor profiles")
    public ResponseEntity<ApiResponse<List<VendorSummaryDto>>> getAllVendors() {
        List<VendorSummaryDto> vendors = vendorProfileRepository.findAll()
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Vendors retrieved", vendors));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending vendors", description = "Get all unverified vendors awaiting approval")
    public ResponseEntity<ApiResponse<List<VendorSummaryDto>>> getPendingVendors() {
        List<VendorSummaryDto> vendors = vendorProfileRepository.findByIsVerified(false)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Pending vendors retrieved", vendors));
    }

    @GetMapping("/verified")
    @Operation(summary = "Get verified vendors", description = "Get all verified vendors")
    public ResponseEntity<ApiResponse<List<VendorSummaryDto>>> getVerifiedVendors() {
        List<VendorSummaryDto> vendors = vendorProfileRepository.findByIsVerified(true)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Verified vendors retrieved", vendors));
    }

    @GetMapping("/{publicVendorId}")
    @Operation(summary = "Get vendor detail", description = "Get full vendor profile details for admin review")
    public ResponseEntity<ApiResponse<AdminVendorDetailDto>> getVendorDetail(
            @PathVariable String publicVendorId) {
        VendorProfile vendor = getVendor(publicVendorId);
        return ResponseEntity.ok(ApiResponse.success("Vendor detail retrieved", toDetail(vendor)));
    }

    // ========== VERIFY / UNVERIFY ==========

    @PatchMapping("/{publicVendorId}/verify")
    @Operation(summary = "Verify vendor", description = "Approve and verify a vendor's business")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> verifyVendor(
            @PathVariable String publicVendorId) {

        VendorProfile vendor = getVendor(publicVendorId);
        vendor.setIsVerified(true);
        vendor.setVerifiedAt(LocalDateTime.now());
        vendorProfileRepository.save(vendor);

        return ResponseEntity.ok(ApiResponse.success(
                "Vendor verified successfully", toSummary(vendor)));
    }

    @PatchMapping("/{publicVendorId}/unverify")
    @Operation(summary = "Revoke vendor verification", description = "Revoke a vendor's verified status")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> unverifyVendor(
            @PathVariable String publicVendorId) {

        VendorProfile vendor = getVendor(publicVendorId);
        vendor.setIsVerified(false);
        vendor.setVerifiedAt(null);
        vendorProfileRepository.save(vendor);

        return ResponseEntity.ok(ApiResponse.success(
                "Vendor verification revoked", toSummary(vendor)));
    }

    // ========== ACTIVATE / DEACTIVATE ==========

    @PatchMapping("/{publicVendorId}/activate")
    @Operation(summary = "Activate vendor", description = "Activate a suspended vendor profile")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> activateVendor(
            @PathVariable String publicVendorId) {

        VendorProfile vendor = getVendor(publicVendorId);
        vendor.setIsActive(true);
        vendorProfileRepository.save(vendor);

        return ResponseEntity.ok(ApiResponse.success(
                "Vendor activated successfully", toSummary(vendor)));
    }

    @PatchMapping("/{publicVendorId}/deactivate")
    @Operation(summary = "Deactivate vendor", description = "Suspend a vendor profile (prevents receiving orders)")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> deactivateVendor(
            @PathVariable String publicVendorId) {

        VendorProfile vendor = getVendor(publicVendorId);
        vendor.setIsActive(false);
        vendorProfileRepository.save(vendor);

        return ResponseEntity.ok(ApiResponse.success(
                "Vendor deactivated successfully", toSummary(vendor)));
    }

    // ========== HELPER METHODS ==========

    private VendorProfile getVendor(String publicVendorId) {
        return vendorProfileRepository.findByPublicVendorId(publicVendorId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Vendor not found with ID: " + publicVendorId));
    }

    private VendorSummaryDto toSummary(VendorProfile vendor) {
        return VendorSummaryDto.builder()
                .publicVendorId(vendor.getUser() != null ? vendor.getUser().getPublicUserId() : null)
                .restaurantName(vendor.getRestaurantName())
                .cuisineType(vendor.getCuisineType())
                .isVerified(vendor.getIsVerified())
                .isActive(vendor.getIsActive())
                .verifiedAt(vendor.getVerifiedAt())
                .createdAt(vendor.getCreatedAt())
                .build();
    }

    private AdminVendorDetailDto toDetail(VendorProfile v) {
        com.afrochow.user.model.User u = v.getUser();
        com.afrochow.address.model.Address a = v.getAddress();
        return AdminVendorDetailDto.builder()
                .publicVendorId(u != null ? u.getPublicUserId() : null)
                // Owner / Account
                .firstName(u != null ? u.getFirstName() : null)
                .lastName(u != null ? u.getLastName() : null)
                .email(u != null ? u.getEmail() : null)
                .phone(u != null ? u.getPhone() : null)
                // Store
                .restaurantName(v.getRestaurantName())
                .description(v.getDescription())
                .cuisineType(v.getCuisineType())
                .logoUrl(v.getLogoUrl())
                .bannerUrl(v.getBannerUrl())
                .taxId(v.getTaxId())
                .businessLicenseUrl(v.getBusinessLicenseUrl())
                // Status
                .isVerified(v.getIsVerified())
                .isActive(v.getIsActive())
                .verifiedAt(v.getVerifiedAt())
                // Operations
                .offersDelivery(v.getOffersDelivery())
                .offersPickup(v.getOffersPickup())
                .preparationTime(v.getPreparationTime())
                .deliveryFee(v.getDeliveryFee())
                .minimumOrderAmount(v.getMinimumOrderAmount())
                .estimatedDeliveryMinutes(v.getEstimatedDeliveryMinutes())
                .maxDeliveryDistanceKm(v.getMaxDeliveryDistanceKm())
                // Operating hours
                .operatingHours(v.getOperatingHours())
                // Address
                .addressLine(a != null ? a.getAddressLine() : null)
                .city(a != null ? a.getCity() : null)
                .province(a != null && a.getProvince() != null ? a.getProvince().name() : null)
                .postalCode(a != null ? a.getPostalCode() : null)
                .country(a != null ? a.getCountry() : null)
                // Timestamps
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }

    // ========== INNER CLASS ==========

    @lombok.Data
    @lombok.Builder
    public static class VendorSummaryDto {
        private String publicVendorId;
        private String restaurantName;
        private String cuisineType;
        private Boolean isVerified;
        private Boolean isActive;
        private java.time.LocalDateTime verifiedAt;
        private java.time.LocalDateTime createdAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class AdminVendorDetailDto {
        // Identity
        private String publicVendorId;
        // Owner / Account
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        // Store
        private String restaurantName;
        private String description;
        private String cuisineType;
        private String logoUrl;
        private String bannerUrl;
        private String taxId;
        private String businessLicenseUrl;
        // Status
        private Boolean isVerified;
        private Boolean isActive;
        private LocalDateTime verifiedAt;
        // Operations
        private Boolean offersDelivery;
        private Boolean offersPickup;
        private Integer preparationTime;
        private BigDecimal deliveryFee;
        private BigDecimal minimumOrderAmount;
        private Integer estimatedDeliveryMinutes;
        private BigDecimal maxDeliveryDistanceKm;
        // Operating hours — Map<dayName, DayHours>
        private Map<String, VendorProfile.DayHours> operatingHours;
        // Address (flat for easy frontend consumption)
        private String addressLine;
        private String city;
        private String province;
        private String postalCode;
        private String country;
        // Timestamps
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
