package com.afrochow.admin.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.common.enums.VendorStatus;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final OutboxEventService       outboxEventService;

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
    @Operation(summary = "Get pending vendors", description = "Vendors in PENDING_REVIEW status awaiting admin action")
    public ResponseEntity<ApiResponse<List<VendorSummaryDto>>> getPendingVendors() {
        List<VendorSummaryDto> vendors = vendorProfileRepository
                .findByVendorStatus(VendorStatus.PENDING_REVIEW)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Pending vendors retrieved", vendors));
    }

    @GetMapping("/provisional")
    @Operation(summary = "Get provisional vendors",
               description = "Vendors approved provisionally — live but food handling cert not yet verified")
    public ResponseEntity<ApiResponse<List<VendorSummaryDto>>> getProvisionalVendors() {
        List<VendorSummaryDto> vendors = vendorProfileRepository
                .findByVendorStatus(VendorStatus.PROVISIONAL)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Provisional vendors retrieved", vendors));
    }

    @GetMapping("/verified")
    @Operation(summary = "Get verified vendors", description = "Fully verified vendors")
    public ResponseEntity<ApiResponse<List<VendorSummaryDto>>> getVerifiedVendors() {
        List<VendorSummaryDto> vendors = vendorProfileRepository
                .findByVendorStatus(VendorStatus.VERIFIED)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Verified vendors retrieved", vendors));
    }

    @GetMapping("/by-status/{status}")
    @Operation(summary = "Get vendors by status", description = "Get vendors in any specific status")
    public ResponseEntity<ApiResponse<List<VendorSummaryDto>>> getVendorsByStatus(
            @PathVariable VendorStatus status) {
        List<VendorSummaryDto> vendors = vendorProfileRepository
                .findByVendorStatus(status)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Vendors retrieved for status: " + status, vendors));
    }

    @GetMapping("/{publicVendorId}")
    @Operation(summary = "Get vendor detail", description = "Get full vendor profile details for admin review")
    public ResponseEntity<ApiResponse<AdminVendorDetailDto>> getVendorDetail(
            @PathVariable String publicVendorId) {
        VendorProfile vendor = getVendor(publicVendorId);
        return ResponseEntity.ok(ApiResponse.success("Vendor detail retrieved", toDetail(vendor)));
    }

    // ========== STATUS TRANSITIONS ==========

    @Transactional
    @PatchMapping("/{publicVendorId}/approve-provisional")
    @Operation(summary = "Approve vendor provisionally",
               description = "Move a PENDING_REVIEW vendor to PROVISIONAL. " +
                             "Vendor goes live; cert upload still required for full verification.")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> approveProvisional(
            @PathVariable String publicVendorId) {

        VendorProfile vendor = getVendor(publicVendorId);

        if (vendor.getVendorStatus() != VendorStatus.PENDING_REVIEW) {
            return ResponseEntity.badRequest().body(ApiResponse.<VendorSummaryDto>builder()
                    .success(false)
                    .message("Vendor must be in PENDING_REVIEW to approve provisionally. Current: "
                             + vendor.getVendorStatus())
                    .build());
        }

        vendor.setVendorStatus(VendorStatus.PROVISIONAL);
        vendor.setIsActive(true);
        vendor.setIsVerified(false);
        if (vendor.getUser() != null) vendor.getUser().setIsActive(true);
        vendorProfileRepository.save(vendor);

        if (vendor.getUser() != null) {
            outboxEventService.vendorApproved(
                    vendor.getUser().getPublicUserId(),
                    vendor.getUser().getEmail(),
                    vendor.getUser().getFirstName(),
                    vendor.getRestaurantName());
        }

        return ResponseEntity.ok(ApiResponse.success("Vendor approved provisionally", toSummary(vendor)));
    }

    @Transactional
    @PatchMapping("/{publicVendorId}/verify-cert")
    @Operation(summary = "Verify food handling certificate",
               description = "Confirm the vendor's food handling certificate and promote to VERIFIED status.")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> verifyCertAndPromote(
            @PathVariable String publicVendorId,
            @AuthenticationPrincipal CustomUserDetails adminDetails) {

        VendorProfile vendor = getVendor(publicVendorId);

        if (vendor.getVendorStatus() != VendorStatus.PROVISIONAL) {
            return ResponseEntity.badRequest().body(ApiResponse.<VendorSummaryDto>builder()
                    .success(false)
                    .message("Vendor must be in PROVISIONAL status to verify cert. Current: "
                             + vendor.getVendorStatus())
                    .build());
        }

        if (!vendor.hasFoodHandlingCert()) {
            return ResponseEntity.badRequest().body(ApiResponse.<VendorSummaryDto>builder()
                    .success(false)
                    .message("Vendor has not uploaded a food handling certificate yet.")
                    .build());
        }

        if (vendor.isCertExpired()) {
            return ResponseEntity.badRequest().body(ApiResponse.<VendorSummaryDto>builder()
                    .success(false)
                    .message("The uploaded certificate has already expired.")
                    .build());
        }

        vendor.setVendorStatus(VendorStatus.VERIFIED);
        vendor.setVerifiedAt(LocalDateTime.now());
        vendor.setCertVerifiedAt(LocalDateTime.now());
        vendor.setCertVerifiedByAdminId(
                adminDetails != null ? adminDetails.getPublicUserId() : "system");
        // Keep deprecated booleans in sync
        vendor.setIsVerified(true);
        vendor.setIsActive(true);
        if (vendor.getUser() != null) vendor.getUser().setIsActive(true);
        vendorProfileRepository.save(vendor);

        return ResponseEntity.ok(ApiResponse.success(
                "Certificate verified — vendor is now fully verified", toSummary(vendor)));
    }

    @Transactional
    @PatchMapping("/{publicVendorId}/verify")
    @Operation(summary = "Fully verify vendor (bypass cert)",
               description = "Directly promote a vendor to VERIFIED without requiring cert upload. " +
                             "Use only in exceptional circumstances (e.g. manual offline verification).")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> verifyVendor(
            @PathVariable String publicVendorId,
            @AuthenticationPrincipal CustomUserDetails adminDetails) {

        VendorProfile vendor = getVendor(publicVendorId);
        vendor.setVendorStatus(VendorStatus.VERIFIED);
        vendor.setVerifiedAt(LocalDateTime.now());
        vendor.setIsVerified(true);
        vendor.setIsActive(true);
        if (vendor.getUser() != null) vendor.getUser().setIsActive(true);
        vendorProfileRepository.save(vendor);

        if (vendor.getUser() != null) {
            outboxEventService.vendorApproved(
                    vendor.getUser().getPublicUserId(),
                    vendor.getUser().getEmail(),
                    vendor.getUser().getFirstName(),
                    vendor.getRestaurantName());
        }

        return ResponseEntity.ok(ApiResponse.success("Vendor fully verified", toSummary(vendor)));
    }

    @Transactional
    @PatchMapping("/{publicVendorId}/suspend")
    @Operation(summary = "Suspend vendor", description = "Suspend a live vendor (VERIFIED or PROVISIONAL). Prevents receiving orders.")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> suspendVendor(
            @PathVariable String publicVendorId) {

        VendorProfile vendor = getVendor(publicVendorId);

        if (vendor.getVendorStatus() != VendorStatus.VERIFIED
                && vendor.getVendorStatus() != VendorStatus.PROVISIONAL) {
            return ResponseEntity.badRequest().body(ApiResponse.<VendorSummaryDto>builder()
                    .success(false)
                    .message("Only VERIFIED or PROVISIONAL vendors can be suspended. Current: "
                             + vendor.getVendorStatus())
                    .build());
        }

        vendor.setVendorStatus(VendorStatus.SUSPENDED);
        vendor.setIsActive(false);
        if (vendor.getUser() != null) vendor.getUser().setIsActive(false);
        vendorProfileRepository.save(vendor);

        if (vendor.getUser() != null) {
            outboxEventService.vendorSuspended(
                    vendor.getUser().getPublicUserId(),
                    vendor.getUser().getEmail(),
                    vendor.getUser().getFirstName(),
                    vendor.getRestaurantName());
        }

        return ResponseEntity.ok(ApiResponse.success("Vendor suspended", toSummary(vendor)));
    }

    @Transactional
    @PatchMapping("/{publicVendorId}/reinstate")
    @Operation(summary = "Reinstate vendor", description = "Reinstate a SUSPENDED vendor back to VERIFIED.")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> reinstateVendor(
            @PathVariable String publicVendorId) {

        VendorProfile vendor = getVendor(publicVendorId);

        if (vendor.getVendorStatus() != VendorStatus.SUSPENDED) {
            return ResponseEntity.badRequest().body(ApiResponse.<VendorSummaryDto>builder()
                    .success(false)
                    .message("Only SUSPENDED vendors can be reinstated. Current: "
                             + vendor.getVendorStatus())
                    .build());
        }

        vendor.setVendorStatus(VendorStatus.VERIFIED);
        vendor.setIsActive(true);
        if (vendor.getUser() != null) vendor.getUser().setIsActive(true);
        vendorProfileRepository.save(vendor);

        if (vendor.getUser() != null) {
            outboxEventService.vendorReinstated(
                    vendor.getUser().getPublicUserId(),
                    vendor.getUser().getEmail(),
                    vendor.getUser().getFirstName(),
                    vendor.getRestaurantName());
        }

        return ResponseEntity.ok(ApiResponse.success("Vendor reinstated", toSummary(vendor)));
    }

    // ========== REJECT ==========

    @lombok.Data
    public static class RejectRequestDto {
        private String reason;
    }

    @Transactional
    @PostMapping("/{publicVendorId}/reject")
    @Operation(summary = "Reject vendor application",
               description = "Reject a vendor at PENDING_REVIEW or PROVISIONAL stage. Sends rejection email with reason.")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> rejectVendor(
            @PathVariable String publicVendorId,
            @RequestBody(required = false) RejectRequestDto body) {

        VendorProfile vendor = getVendor(publicVendorId);

        if (vendor.getVendorStatus() == VendorStatus.VERIFIED
                || vendor.getVendorStatus() == VendorStatus.SUSPENDED) {
            return ResponseEntity.badRequest().body(ApiResponse.<VendorSummaryDto>builder()
                    .success(false)
                    .message("Use /suspend to remove a verified vendor. Reject is for pending/provisional vendors.")
                    .build());
        }

        vendor.setVendorStatus(VendorStatus.REJECTED);
        vendor.setIsActive(false);
        vendor.setIsVerified(false);
        if (vendor.getUser() != null) vendor.getUser().setIsActive(false);
        vendorProfileRepository.save(vendor);

        if (vendor.getUser() != null) {
            String reason = (body != null) ? body.getReason() : null;
            outboxEventService.vendorRejected(
                    vendor.getUser().getPublicUserId(),
                    vendor.getUser().getEmail(),
                    vendor.getUser().getFirstName(),
                    vendor.getRestaurantName(),
                    reason);
        }

        return ResponseEntity.ok(ApiResponse.success("Vendor application rejected", toSummary(vendor)));
    }

    // ========== DEPRECATED (kept for compatibility) ==========

    /** @deprecated Use /approve-provisional or /verify instead */
    @Deprecated
    @Transactional
    @PatchMapping("/{publicVendorId}/activate")
    @Operation(summary = "[Deprecated] Activate vendor", description = "Deprecated — use /reinstate for suspended vendors")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> activateVendor(
            @PathVariable String publicVendorId) {
        return reinstateVendor(publicVendorId);
    }

    /** @deprecated Use /suspend instead */
    @Deprecated
    @Transactional
    @PatchMapping("/{publicVendorId}/deactivate")
    @Operation(summary = "[Deprecated] Deactivate vendor", description = "Deprecated — use /suspend instead")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> deactivateVendor(
            @PathVariable String publicVendorId) {
        return suspendVendor(publicVendorId);
    }

    // ========== STRIPE ACCOUNT LINKING ==========

    @lombok.Data
    public static class LinkStripeAccountDto {
        private String stripeAccountId;
    }

    @Transactional
    @PatchMapping("/{publicVendorId}/stripe-account")
    @Operation(summary = "Link Stripe account", description = "Link or replace a vendor's Stripe Connect account ID")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> linkStripeAccount(
            @PathVariable String publicVendorId,
            @RequestBody LinkStripeAccountDto body) {

        String accountId = body.getStripeAccountId();
        if (accountId == null || accountId.isBlank() || !accountId.trim().startsWith("acct_")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<VendorSummaryDto>builder()
                            .success(false)
                            .message("Invalid Stripe account ID — must start with 'acct_'")
                            .build());
        }

        // Verify the account actually exists in Stripe and belongs to our platform
        Account stripeAccount;
        try {
            stripeAccount = Account.retrieve(accountId.trim());
        } catch (InvalidRequestException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<VendorSummaryDto>builder()
                            .success(false)
                            .message("Stripe account not found: " + accountId.trim())
                            .build());
        } catch (StripeException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<VendorSummaryDto>builder()
                            .success(false)
                            .message("Could not verify Stripe account: " + e.getMessage())
                            .build());
        }

        boolean onboardingComplete = Boolean.TRUE.equals(stripeAccount.getDetailsSubmitted());

        VendorProfile vendor = getVendor(publicVendorId);
        vendor.setStripeAccountId(accountId.trim());
        vendor.setStripeOnboardingComplete(onboardingComplete);
        vendorProfileRepository.save(vendor);

        String msg = onboardingComplete
                ? "Stripe account linked and marked as fully onboarded"
                : "Stripe account linked — onboarding not yet complete on Stripe";
        return ResponseEntity.ok(ApiResponse.success(msg, toSummary(vendor)));
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
                .vendorStatus(vendor.getVendorStatus())
                .isVerified(vendor.getIsVerified())
                .isActive(vendor.getIsActive())
                .verifiedAt(vendor.getVerifiedAt())
                .createdAt(vendor.getCreatedAt())
                .stripeOnboardingComplete(vendor.getStripeOnboardingComplete())
                .hasFoodHandlingCert(vendor.hasFoodHandlingCert())
                .certVerifiedAt(vendor.getCertVerifiedAt())
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
                .vendorStatus(v.getVendorStatus())
                .isVerified(v.getIsVerified())
                .isActive(v.getIsActive())
                .verifiedAt(v.getVerifiedAt())
                // Food Handling Certificate
                .foodHandlingCertUrl(v.getFoodHandlingCertUrl())
                .foodHandlingCertNumber(v.getFoodHandlingCertNumber())
                .foodHandlingCertIssuingBody(v.getFoodHandlingCertIssuingBody())
                .foodHandlingCertExpiry(v.getFoodHandlingCertExpiry())
                .certExpired(v.isCertExpired())
                .certVerifiedAt(v.getCertVerifiedAt())
                .certVerifiedByAdminId(v.getCertVerifiedByAdminId())
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
                // Stripe
                .stripeAccountId(v.getStripeAccountId())
                .stripeOnboardingComplete(v.getStripeOnboardingComplete())
                .build();
    }

    // ========== INNER CLASS ==========

    @lombok.Data
    @lombok.Builder
    public static class VendorSummaryDto {
        private String publicVendorId;
        private String restaurantName;
        private String cuisineType;
        private VendorStatus vendorStatus;
        /** @deprecated Use vendorStatus */
        @Deprecated private Boolean isVerified;
        /** @deprecated Use vendorStatus */
        @Deprecated private Boolean isActive;
        private LocalDateTime verifiedAt;
        private LocalDateTime createdAt;
        private Boolean stripeOnboardingComplete;
        private Boolean hasFoodHandlingCert;
        private LocalDateTime certVerifiedAt;
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
        private VendorStatus vendorStatus;
        /** @deprecated Use vendorStatus */ @Deprecated private Boolean isVerified;
        /** @deprecated Use vendorStatus */ @Deprecated private Boolean isActive;
        private LocalDateTime verifiedAt;
        // Food Handling Certificate
        private String foodHandlingCertUrl;
        private String foodHandlingCertNumber;
        private String foodHandlingCertIssuingBody;
        private LocalDateTime foodHandlingCertExpiry;
        private Boolean certExpired;
        private LocalDateTime certVerifiedAt;
        private String certVerifiedByAdminId;
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
        // Stripe
        private String stripeAccountId;
        private Boolean stripeOnboardingComplete;
    }
}
