package com.afrochow.vendor.service;

import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.LoginLink;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles Stripe Connect Express onboarding for vendors.
 *
 * Flow:
 * 1. POST /vendor/stripe/connect     → createConnectAccount() creates a Stripe Express account,
 *                                       saves stripeAccountId, returns onboarding URL.
 * 2. Vendor completes Stripe-hosted onboarding form.
 * 3. Stripe fires account.updated webhook → StripeWebhookController marks stripeOnboardingComplete=true.
 * 4. GET /vendor/stripe/connect/dashboard → generateDashboardLink() for ongoing payouts access.
 */
@Service
@RequiredArgsConstructor
public class StripeConnectService {

    private final VendorProfileRepository vendorProfileRepository;

    @Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    /**
     * Creates a Stripe Express account for the vendor (if they don't already have one)
     * and returns the onboarding URL.
     */
    @Transactional
    public String createConnectAccountAndGetOnboardingUrl(Long userId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));

        try {
            String stripeAccountId = vendor.getStripeAccountId();

            if (stripeAccountId == null || stripeAccountId.isBlank()) {
                // Create a new Express account
                AccountCreateParams params = AccountCreateParams.builder()
                        .setType(AccountCreateParams.Type.EXPRESS)
                        .setEmail(vendor.getUser().getEmail())
                        .setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL)
                        .putMetadata("vendorId", String.valueOf(vendor.getId()))
                        .putMetadata("publicVendorId", vendor.getPublicVendorId())
                        .build();

                Account account = Account.create(params);
                stripeAccountId = account.getId();
                vendor.setStripeAccountId(stripeAccountId);
                vendorProfileRepository.save(vendor);
            }

            return generateOnboardingLink(stripeAccountId);
        } catch (StripeException e) {
            throw new RuntimeException("Failed to create Stripe Connect account: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a fresh onboarding link for an existing Stripe account.
     * Called when the vendor wants to resume incomplete onboarding.
     */
    public String generateOnboardingLink(String stripeAccountId) throws StripeException {
        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(stripeAccountId)
                .setRefreshUrl(appBaseUrl + "/vendor/settings?stripe=refresh")
                .setReturnUrl(appBaseUrl + "/vendor/settings?stripe=return")
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();

        AccountLink link = AccountLink.create(params);
        return link.getUrl();
    }

    /**
     * Generates a Stripe Express dashboard login link so the vendor can
     * view payouts, transactions, and update banking details.
     */
    public String generateDashboardLink(Long userId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));

        String stripeAccountId = vendor.getStripeAccountId();
        if (stripeAccountId == null || stripeAccountId.isBlank()) {
            throw new IllegalStateException("No Stripe account connected. Please complete onboarding first.");
        }

        try {
            LoginLink link = LoginLink.createOnAccount(stripeAccountId, null, null);
            return link.getUrl();
        } catch (StripeException e) {
            throw new RuntimeException("Failed to generate Stripe dashboard link: " + e.getMessage(), e);
        }
    }

    /**
     * Called by StripeWebhookController when account.updated is received and
     * the account has completed requirements (details_submitted = true).
     */
    @Transactional
    public void markOnboardingComplete(String stripeAccountId) {
        vendorProfileRepository.findByStripeAccountId(stripeAccountId)
                .ifPresent(vendor -> {
                    vendor.setStripeOnboardingComplete(true);
                    vendorProfileRepository.save(vendor);
                });
    }
}
