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
// VENDORS area = OPERATIONS department (or SUPERADMIN). linkStripeAccount()
// below overrides this with a stricter hasRole('SUPERADMIN') — untouched.
@PreAuthorize("@deptAccess.can('VENDORS')")
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
                .filter(this::hasVerifiedOwnerEmail)
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
                             "Vendor goes live once Stripe Connect is payout-ready; documents can follow.")
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

        ResponseEntity<ApiResponse<VendorSummaryDto>> emailGuard = requireVerifiedOwnerEmail(vendor);
        if (emailGuard != null) {
            return emailGuard;
        }

        ResponseEntity<ApiResponse<VendorSummaryDto>> payoutGuard = requireStripePayoutReady(vendor);
        if (payoutGuard != null) {
            return payoutGuard;
        }

        vendor.setVendorStatus(VendorStatus.PROVISIONAL);
        vendor.setIsActive(true);
        vendor.setIsVerified(false);
        if (vendor.getUser() != null) vendor.getUser().setIsActive(true);
        vendorProfileRepository.save(vendor);

        if (vendor.getUser() != null) {
            // Fire the provisional event — sends the provisional email (documents still required)
            outboxEventService.vendorProvisional(
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

        if (vendor.getUser() != null) {
            // Fire the full approval event — sends the "fully verified" email
            outboxEventService.vendorApproved(
                    vendor.getUser().getPublicUserId(),
                    vendor.getUser().getEmail(),
                    vendor.getUser().getFirstName(),
                    vendor.getRestaurantName());
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Certificate verified — vendor is now fully verified", toSummary(vendor)));
    }

    @Transactional
    @PatchMapping("/{publicVendorId}/verify")
    @Operation(summary = "Fully verify vendor (bypass cert)",
               description = "Directly promote a vendor to VERIFIED without requiring cert upload. " +
                             "Use only in exceptional circumstances (e.g. manual offline verification). " +
                             "Stripe Connect must still be payout-ready before activation.")
    public ResponseEntity<ApiResponse<VendorSummaryDto>> verifyVendor(
            @PathVariable String publicVendorId,
            @AuthenticationPrincipal CustomUserDetails adminDetails) {

        VendorProfile vendor = getVendor(publicVendorId);

        ResponseEntity<ApiResponse<VendorSummaryDto>> emailGuard = requireVerifiedOwnerEmail(vendor);
        if (emailGuard != null) {
            return emailGuard;
        }

        ResponseEntity<ApiResponse<VendorSummaryDto>> payoutGuard = requireStripePayoutReady(vendor);
        if (payoutGuard != null) {
            return payoutGuard;
        }

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
        // Deliberately NOT touching User.isActive: resubmitForReview() is a
        // self-service, authenticated ("hasRole('VENDOR')") endpoint the vendor
        // hits themselves after fixing their profile. Disabling the user account
        // here (CustomUserDetails.isEnabled() requires isActive) would lock them
        // out of login entirely, making that whole resubmit flow unreachable —
        // there's no admin "un-reject" endpoint to undo it, unlike /reinstate for
        // SUSPENDED vendors. The vendor profile itself (isActive/isVerified above)
        // already keeps the store hidden and unable to receive orders.
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

    // ========== STRIPE ACCOUNT LINKING ==========

    @lombok.Data
    public static class LinkStripeAccountDto {
        private String stripeAccountId;
    }

    @Transactional
    @PatchMapping("/{publicVendorId}/stripe-account")
    // Overrides the class-level hasAnyRole(ADMIN, SUPERADMIN) — this endpoint
    // redirects where a vendor's payouts go, so it's held to the same
    // SUPERADMIN-only bar as user role changes, user deletion, and product
    // deletion elsewhere in the admin API.
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Link Stripe account", description = "Link or replace a vendor's Stripe Connect account ID — SUPERADMIN only")
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
        boolean chargesEnabled = Boolean.TRUE.equals(stripeAccount.getChargesEnabled());
        boolean payoutsEnabled = Boolean.TRUE.equals(stripeAccount.getPayoutsEnabled());
        String disabledReason = stripeAccount.getRequirements() != null
                ? stripeAccount.getRequirements().getDisabledReason()
                : null;

        VendorProfile vendor = getVendor(publicVendorId);
        vendor.setStripeAccountId(accountId.trim());
        vendor.setStripeOnboardingComplete(onboardingComplete);
        vendor.setStripeChargesEnabled(chargesEnabled);
        vendor.setStripePayoutsEnabled(payoutsEnabled);
        vendor.setStripeRequirementsDisabledReason(disabledReason);
        vendorProfileRepository.save(vendor);

        String msg = vendor.isPayoutReady()
                ? "Stripe account linked and payout-ready"
                : "Stripe account linked — Stripe still requires setup before the vendor can take paid orders";
        return ResponseEntity.ok(ApiResponse.success(msg, toSummary(vendor)));
    }

    // ========== HELPER METHODS ==========

    private VendorProfile getVendor(String publicVendorId) {
        return vendorProfileRepository.findByPublicVendorId(publicVendorId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Vendor not found with ID: " + publicVendorId));
    }

    private boolean hasVerifiedOwnerEmail(VendorProfile vendor) {
        return vendor.getUser() != null && Boolean.TRUE.equals(vendor.getUser().getEmailVerified());
    }

    private ResponseEntity<ApiResponse<VendorSummaryDto>> requireVerifiedOwnerEmail(VendorProfile vendor) {
        if (hasVerifiedOwnerEmail(vendor)) {
            return null;
        }

        return ResponseEntity.badRequest().body(ApiResponse.<VendorSummaryDto>builder()
                .success(false)
                .message("Vendor owner must verify their email before admin approval.")
                .build());
    }

    private ResponseEntity<ApiResponse<VendorSummaryDto>> requireStripePayoutReady(VendorProfile vendor) {
        if (vendor.isPayoutReady()) {
            return null;
        }

        return ResponseEntity.badRequest().body(ApiResponse.<VendorSummaryDto>builder()
                .success(false)
                .message("Vendor must complete Stripe Connect onboarding before admin approval.")
                .build());
    }

    private VendorSummaryDto toSummary(VendorProfile vendor) {
        com.afrochow.user.model.User owner = vendor.getUser();
        return VendorSummaryDto.builder()
                .publicVendorId(owner != null ? owner.getPublicUserId() : null)
                .firstName(owner != null ? owner.getFirstName() : null)
                .lastName(owner != null ? owner.getLastName() : null)
                .email(owner != null ? owner.getEmail() : null)
                .phone(owner != null ? owner.getPhone() : null)
                .restaurantName(vendor.getRestaurantName())
                .storeCategory(vendor.getStoreCategory())
                .vendorStatus(vendor.getVendorStatus())
                .isVerified(vendor.getIsVerified())
                .isActive(vendor.getIsActive())
                .verifiedAt(vendor.getVerifiedAt())
                .createdAt(vendor.getCreatedAt())
                .stripeAccountId(vendor.getStripeAccountId())
                .stripeOnboardingComplete(vendor.getStripeOnboardingComplete())
                .stripeChargesEnabled(vendor.getStripeChargesEnabled())
                .stripePayoutsEnabled(vendor.getStripePayoutsEnabled())
                .stripeRequirementsDisabledReason(vendor.getStripeRequirementsDisabledReason())
                .payoutReady(vendor.isPayoutReady())
                .isSeedData(vendor.getIsSeedData())
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
                .storeCategory(v.getStoreCategory())
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
                .stripeChargesEnabled(v.getStripeChargesEnabled())
                .stripePayoutsEnabled(v.getStripePayoutsEnabled())
                .stripeRequirementsDisabledReason(v.getStripeRequirementsDisabledReason())
                .payoutReady(v.isPayoutReady())
                .isSeedData(v.getIsSeedData())
                .build();
    }

    // ========== INNER CLASS ==========

    @lombok.Data
    @lombok.Builder
    public static class VendorSummaryDto {
        private String publicVendorId;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String restaurantName;
        private String storeCategory;
        private VendorStatus vendorStatus;
        /** @deprecated Use vendorStatus */
        @Deprecated private Boolean isVerified;
        /** @deprecated Use vendorStatus */
        @Deprecated private Boolean isActive;
        private LocalDateTime verifiedAt;
        private LocalDateTime createdAt;
        private String stripeAccountId;
        private Boolean stripeOnboardingComplete;
        private Boolean stripeChargesEnabled;
        private Boolean stripePayoutsEnabled;
        private String stripeRequirementsDisabledReason;
        /**
         * Whether this vendor can be paid, and so whether customers can order from them.
         * Requires BOTH a Stripe account and completed onboarding — see
         * {@link com.afrochow.vendor.model.VendorProfile#isPayoutReady()}. This is the
         * field to check when a restaurant can't accept orders.
         */
        private Boolean payoutReady;
        /** True for demo/showroom vendors, false for real registrations. */
        private Boolean isSeedData;
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
        private String storeCategory;
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
        private Boolean stripeChargesEnabled;
        private Boolean stripePayoutsEnabled;
        private String stripeRequirementsDisabledReason;
        /** See {@link VendorSummaryDto#payoutReady}. */
        private Boolean payoutReady;
        /** True for demo/showroom vendors, false for real registrations. */
        private Boolean isSeedData;
    }
}
