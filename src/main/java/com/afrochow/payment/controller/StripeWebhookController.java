package com.afrochow.payment.controller;

import com.afrochow.vendor.service.StripeConnectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Account;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Receives Stripe webhook events.
 *
 * This endpoint is intentionally unauthenticated — Stripe calls it directly.
 * Security is provided by verifying the stripe-signature header against the
 * webhook endpoint secret configured in your Stripe dashboard.
 *
 * Register this URL in your Stripe dashboard:
 *   https://dashboard.stripe.com/webhooks → Add endpoint → {APP_URL}/api/stripe/webhook
 *
 * Events handled:
 *   - account.updated                   → marks vendor stripeOnboardingComplete=true when details_submitted
 *   - v2.core.account_link.returned     → fetches account from Stripe API, marks complete if details_submitted
 */
@RestController
@RequestMapping("/stripe/webhook")
@Tag(name = "Stripe Webhook", description = "Webhook receiver for Stripe events")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StripeConnectService stripeConnectService;
    private final Environment environment;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    public StripeWebhookController(StripeConnectService stripeConnectService, Environment environment) {
        this.stripeConnectService = stripeConnectService;
        this.environment = environment;
    }

    @PostMapping
    @Operation(summary = "Receive Stripe webhook",
               description = "Processes Stripe webhook events. Endpoint must be registered in Stripe dashboard.")
    public ResponseEntity<String> handleWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader
    ) {
        String payload;
        try {
            payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read Stripe webhook payload", e);
            return ResponseEntity.badRequest().body("Cannot read payload");
        }

        Event event;

        try {
            boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            boolean verificationDisabled = webhookSecret == null || webhookSecret.isBlank();

            if (isProd && verificationDisabled) {
                log.error("Stripe webhook secret is missing in production; refusing to process webhooks");
                return ResponseEntity.status(500).body("Webhook misconfigured");
            }

            if (verificationDisabled) {
                // application.properties documents this behavior for local development/testing
                log.warn("Stripe webhook verification disabled (stripe.webhook.secret is blank). Do not use in production.");
                event = Event.GSON.fromJson(payload, Event.class);
            } else {
                if (sigHeader == null || sigHeader.isBlank()) {
                    return ResponseEntity.badRequest().body("Missing Stripe-Signature header");
                }
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            }
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            log.error("Failed to parse Stripe webhook payload", e);
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        log.info("Stripe webhook received: {}", event.getType());
        switch (event.getType()) {
            case "account.updated"                    -> handleAccountUpdated(event);
            case "v2.core.account_link.returned"      -> handleAccountLinkReturned(payload);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }

        return ResponseEntity.ok("received");
    }

    /**
     * Handles Stripe v2 event fired when a vendor completes the AccountLink onboarding flow.
     * Retrieves the full account from Stripe API to check details_submitted.
     */
    private void handleAccountLinkReturned(String payload) {
        try {
            JsonNode root = MAPPER.readTree(payload);
            String accountId = root.path("data").path("account_id").asText(null);
            if (accountId == null || accountId.isBlank()) {
                log.warn("v2.core.account_link.returned missing account_id");
                return;
            }
            Account account = Account.retrieve(accountId);
            if (Boolean.TRUE.equals(account.getDetailsSubmitted())) {
                stripeConnectService.markOnboardingComplete(accountId);
                log.info("Stripe Connect onboarding complete (account_link.returned) for account: {}", accountId);
            } else {
                log.info("account_link.returned for account {} — details_submitted still false", accountId);
            }
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe account for account_link.returned: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing v2.core.account_link.returned event", e);
        }
    }

    private void handleAccountUpdated(Event event) {
        try {
            // Stripe SDK deserialization can silently fail on API version mismatch.
            // Parse the raw JSON directly to avoid empty Optional.
            // getRawJson() returns the data.object JSON (the Account itself, not the wrapper)
            String rawJson = event.getDataObjectDeserializer().getRawJson();
            JsonNode root = MAPPER.readTree(rawJson);

            String accountId       = root.path("id").asText(null);
            boolean detailsSubmitted = root.path("details_submitted").asBoolean(false);
            boolean chargesEnabled   = root.path("charges_enabled").asBoolean(false);

            log.info("account.updated — id={} details_submitted={} charges_enabled={}",
                    accountId, detailsSubmitted, chargesEnabled);

            if (detailsSubmitted && accountId != null && !accountId.isBlank()) {
                stripeConnectService.markOnboardingComplete(accountId);
                log.info("Stripe Connect onboarding complete for account: {}", accountId);
            }
        } catch (Exception e) {
            log.error("Error processing account.updated event", e);
        }
    }
}
