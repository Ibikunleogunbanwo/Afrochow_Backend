package com.afrochow.payment.controller;

import com.afrochow.vendor.service.StripeConnectService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Account;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
 *   - account.updated  → marks vendor stripeOnboardingComplete=true when details_submitted
 */
@RestController
@RequestMapping("/stripe/webhook")
@Tag(name = "Stripe Webhook", description = "Webhook receiver for Stripe events")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeConnectService stripeConnectService;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    public StripeWebhookController(StripeConnectService stripeConnectService) {
        this.stripeConnectService = stripeConnectService;
    }

    @PostMapping
    @Operation(summary = "Receive Stripe webhook",
               description = "Processes Stripe webhook events. Endpoint must be registered in Stripe dashboard.")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        Event event;

        try {
            if (webhookSecret == null || webhookSecret.isBlank()) {
                // Dev mode: skip signature verification (set stripe.webhook.secret in prod)
                event = Event.GSON.fromJson(payload, Event.class);
                log.warn("Stripe webhook received without signature verification — set stripe.webhook.secret in production");
            } else {
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            }
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            log.error("Failed to parse Stripe webhook payload", e);
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        switch (event.getType()) {
            case "account.updated" -> handleAccountUpdated(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }

        return ResponseEntity.ok("received");
    }

    private void handleAccountUpdated(Event event) {
        try {
            event.getDataObjectDeserializer()
                    .getObject()
                    .ifPresent(stripeObject -> {
                        if (stripeObject instanceof Account account) {
                            // details_submitted=true means the vendor finished the onboarding form
                            if (Boolean.TRUE.equals(account.getDetailsSubmitted())) {
                                stripeConnectService.markOnboardingComplete(account.getId());
                                log.info("Stripe Connect onboarding complete for account: {}", account.getId());
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("Error processing account.updated event", e);
        }
    }
}
