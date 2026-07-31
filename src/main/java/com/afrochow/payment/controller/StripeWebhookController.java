package com.afrochow.payment.controller;

import com.afrochow.payment.service.PaymentService;
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
 *   - account.updated                        → marks vendor stripeOnboardingComplete=true when details_submitted
 *   - v2.core.account_link.returned          → fetches account from Stripe API, marks complete if details_submitted
 *   - payment_intent.amount_capturable_updated → reconciles Payment to AUTHORIZED (safety net for missed 3DS confirms)
 *   - payment_intent.succeeded                → reconciles Payment to COMPLETED (safety net for missed captures)
 *   - payment_intent.payment_failed            → reconciles Payment to FAILED (resolves ambiguous client-side timeouts)
 *   - charge.refunded                          → reconciles Payment to REFUNDED (catches dashboard-issued refunds)
 *
 * Register all of these event types on the Stripe dashboard endpoint — the reconciliation
 * events in particular are a safety net, not the primary path, but production payments
 * should not depend solely on synchronous API calls succeeding.
 */
@RestController
@RequestMapping("/stripe/webhook")
@Tag(name = "Stripe Webhook", description = "Webhook receiver for Stripe events")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StripeConnectService stripeConnectService;
    private final PaymentService paymentService;
    private final Environment environment;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    public StripeWebhookController(StripeConnectService stripeConnectService, PaymentService paymentService, Environment environment) {
        this.stripeConnectService = stripeConnectService;
        this.paymentService = paymentService;
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

        String eventType = event == null ? null : event.getType();
        if (eventType == null || eventType.isBlank()) {
            log.warn("Stripe webhook payload missing event type");
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        log.info("Stripe webhook received: {}", eventType);
        try {
            switch (eventType) {
                case "account.updated"                          -> handleAccountUpdated(event);
                case "v2.core.account_link.returned"            -> handleAccountLinkReturned(payload);
                case "payment_intent.amount_capturable_updated" -> handlePaymentIntentAuthorized(event);
                case "payment_intent.succeeded"                 -> handlePaymentIntentSucceeded(event);
                case "payment_intent.payment_failed"            -> handlePaymentIntentFailed(event);
                case "payment_intent.canceled"                  -> handlePaymentIntentCanceled(event);
                case "charge.refunded"                          -> handleChargeRefunded(event);
                case "charge.dispute.created"                   -> handleDisputeCreated(event);
                case "charge.dispute.closed"                    -> handleDisputeClosed(event);
                default -> log.debug("Unhandled Stripe event type: {}", eventType);
            }
        } catch (RuntimeException e) {
            // Return 500 so Stripe redelivers. These handlers ARE the safety net for
            // state our synchronous call chain missed, so swallowing a failure here and
            // reporting 200 told Stripe "handled" and permanently dropped the only
            // remaining chance to reconcile that payment.
            log.error("Stripe webhook handler failed for {} — returning 500 so Stripe retries", eventType, e);
            return ResponseEntity.status(500).body("Handler failed");
        }

        return ResponseEntity.ok("received");
    }

    /**
     * Parses the event's data.object, or returns null if it cannot be read.
     *
     * <p>A malformed payload is a PERMANENT failure — retrying it will fail
     * identically forever — so it is logged and swallowed rather than being turned
     * into a 500 that makes Stripe redeliver on a schedule for days. Failures from
     * the reconciliation calls themselves are the opposite: those are usually
     * transient (DB unavailable, Stripe API hiccup) and must propagate so Stripe
     * retries. That's why the service calls below sit OUTSIDE any catch.
     */
    private JsonNode parseDataObject(Event event, String eventType) {
        try {
            return MAPPER.readTree(event.getDataObjectDeserializer().getRawJson());
        } catch (Exception e) {
            log.error("Could not parse data object for {} — dropping event", eventType, e);
            return null;
        }
    }

    private void handlePaymentIntentAuthorized(Event event) {
        JsonNode root = parseDataObject(event, "payment_intent.amount_capturable_updated");
        if (root == null) return;
        String paymentIntentId = root.path("id").asText(null);
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("payment_intent.amount_capturable_updated missing id");
            return;
        }
        paymentService.reconcilePaymentIntentAuthorized(paymentIntentId);
    }

    private void handlePaymentIntentSucceeded(Event event) {
        JsonNode root = parseDataObject(event, "payment_intent.succeeded");
        if (root == null) return;
        String paymentIntentId = root.path("id").asText(null);
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("payment_intent.succeeded missing id");
            return;
        }
        long amountReceived = root.path("amount_received").asLong(0);
        paymentService.reconcilePaymentIntentCompleted(paymentIntentId, amountReceived > 0 ? amountReceived : null);
    }

    private void handlePaymentIntentFailed(Event event) {
        JsonNode root = parseDataObject(event, "payment_intent.payment_failed");
        if (root == null) return;
        String paymentIntentId = root.path("id").asText(null);
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("payment_intent.payment_failed missing id");
            return;
        }
        String failureMessage = root.path("last_payment_error").path("message").asText("Payment failed");
        paymentService.reconcilePaymentIntentFailed(paymentIntentId, failureMessage);
    }

    /**
     * A PaymentIntent was cancelled — either by our own SLA-expiry path releasing the
     * hold, or from the Stripe dashboard. Reconciles the local record so a payment
     * cancelled outside our synchronous flow doesn't sit AUTHORIZED forever, looking
     * to reconciliation reports like money we are still holding.
     */
    private void handlePaymentIntentCanceled(Event event) {
        JsonNode root = parseDataObject(event, "payment_intent.canceled");
        if (root == null) return;
        String paymentIntentId = root.path("id").asText(null);
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("payment_intent.canceled missing id");
            return;
        }
        paymentService.reconcilePaymentIntentCanceled(paymentIntentId);
    }

    private void handleChargeRefunded(Event event) {
        JsonNode root = parseDataObject(event, "charge.refunded");
        if (root == null) return;
        String paymentIntentId = root.path("payment_intent").asText(null);
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("charge.refunded missing payment_intent");
            return;
        }
        paymentService.reconcileChargeRefunded(paymentIntentId);
    }

    /**
     * A customer disputed a charge (chargeback). Stripe debits the disputed amount
     * from the platform balance immediately, so this needs to be visible in-app
     * rather than only in the Stripe dashboard — especially when the vendor has
     * already been paid out for the order.
     */
    private void handleDisputeCreated(Event event) {
        JsonNode root = parseDataObject(event, "charge.dispute.created");
        if (root == null) return;
        String paymentIntentId = root.path("payment_intent").asText(null);
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("charge.dispute.created missing payment_intent");
            return;
        }
        long amountCents = root.path("amount").asLong(0);
        String reason = root.path("reason").asText("unknown");
        paymentService.recordDisputeOpened(paymentIntentId, amountCents, reason);
    }

    /**
     * A dispute reached a final state. {@code status} is won/lost/warning_closed —
     * only "lost" means the funds are gone for good.
     */
    private void handleDisputeClosed(Event event) {
        JsonNode root = parseDataObject(event, "charge.dispute.closed");
        if (root == null) return;
        String paymentIntentId = root.path("payment_intent").asText(null);
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("charge.dispute.closed missing payment_intent");
            return;
        }
        String status = root.path("status").asText("unknown");
        paymentService.recordDisputeClosed(paymentIntentId, status);
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
            // Transient Stripe-side failure — propagate so the caller returns 500 and
            // Stripe redelivers. Swallowing this left the vendor stuck in "onboarding
            // incomplete" with no further event coming to fix it.
            log.error("Failed to retrieve Stripe account for account_link.returned: {}", e.getMessage());
            throw new IllegalStateException("Could not retrieve Stripe account", e);
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
