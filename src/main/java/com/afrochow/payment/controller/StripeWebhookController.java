package com.afrochow.payment.controller;

import com.afrochow.vendor.service.StripeConnectService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Account;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
            HttpServletRequest request,
            @RequestHeader("Stripe-Signature") String sigHeader
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
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            log.error("Failed to parse Stripe webhook payload", e);
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        log.info("Stripe webhook received: {}", event.getType());
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
