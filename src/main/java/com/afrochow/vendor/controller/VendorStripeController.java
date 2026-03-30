package com.afrochow.vendor.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import com.afrochow.vendor.service.StripeConnectService;
import com.afrochow.vendor.service.VendorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Stripe Connect onboarding endpoints for vendors.
 *
 * POST /vendor/stripe/connect              – Create/retrieve Stripe Express account + return onboarding URL
 * GET  /vendor/stripe/connect/status       – Current connection status
 * GET  /vendor/stripe/connect/onboarding-link – Refresh onboarding link (for incomplete accounts)
 * GET  /vendor/stripe/connect/dashboard    – Stripe Express dashboard login link
 */
@RestController
@RequestMapping("/vendor/stripe/connect")
@Tag(name = "Vendor Stripe Connect", description = "Stripe Connect onboarding and payout management for vendors")
public class VendorStripeController {

    private final StripeConnectService stripeConnectService;
    private final VendorProfileService vendorProfileService;

    public VendorStripeController(StripeConnectService stripeConnectService,
                                   VendorProfileService vendorProfileService) {
        this.stripeConnectService = stripeConnectService;
        this.vendorProfileService = vendorProfileService;
    }

    /**
     * Start or resume Stripe Connect onboarding.
     * Creates an Express account if one doesn't exist yet, then returns the
     * Stripe-hosted onboarding URL the frontend should redirect the vendor to.
     */
    @PostMapping
    @Operation(summary = "Start Stripe Connect onboarding",
               description = "Creates a Stripe Express account (if needed) and returns the onboarding URL")
    public ResponseEntity<ApiResponse<Map<String, String>>> startOnboarding(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        String onboardingUrl = stripeConnectService.createConnectAccountAndGetOnboardingUrl(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "Stripe onboarding URL generated",
                Map.of("onboardingUrl", onboardingUrl)
        ));
    }

    /**
     * Returns the vendor's current Stripe Connect status.
     */
    @GetMapping("/status")
    @Operation(summary = "Get Stripe Connect status",
               description = "Returns stripeAccountId and whether onboarding is complete")
    public ResponseEntity<ApiResponse<VendorProfileResponseDto>> getStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        VendorProfileResponseDto profile = vendorProfileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Stripe connect status retrieved", profile));
    }

    /**
     * Generates a fresh onboarding link for vendors who started but did not
     * finish the Stripe onboarding form (e.g. after a browser refresh).
     */
    @GetMapping("/onboarding-link")
    @Operation(summary = "Refresh onboarding link",
               description = "Returns a fresh Stripe onboarding URL for an account that hasn't completed setup")
    public ResponseEntity<ApiResponse<Map<String, String>>> refreshOnboardingLink(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        String onboardingUrl = stripeConnectService.createConnectAccountAndGetOnboardingUrl(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "Onboarding link refreshed",
                Map.of("onboardingUrl", onboardingUrl)
        ));
    }

    /**
     * Returns a Stripe Express dashboard URL so the vendor can view payouts
     * and update their banking details directly in Stripe.
     */
    @GetMapping("/dashboard")
    @Operation(summary = "Get Stripe dashboard link",
               description = "Returns a login link to the vendor's Stripe Express dashboard")
    public ResponseEntity<ApiResponse<Map<String, String>>> getDashboardLink(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        String dashboardUrl = stripeConnectService.generateDashboardLink(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "Stripe dashboard link generated",
                Map.of("dashboardUrl", dashboardUrl)
        ));
    }
}
