package com.afrochow.payment.controller;

import com.afrochow.payment.service.PaymentService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.vendor.service.StripeConnectService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for StripeWebhookController.
 *
 * This endpoint is unauthenticated by design (Stripe calls it directly) and
 * reads the raw request body itself rather than via {@code @RequestBody}.
 * {@code stripe.webhook.secret} is actually configured in this environment
 * (loaded from .env), so the controller always takes the signature-verified
 * path through {@code Webhook.constructEvent(...)}. Rather than compute a
 * real HMAC signature against a secret this test doesn't know, we statically
 * mock {@code Webhook.constructEvent} (Mockito's inline mock maker, already
 * active for this project, supports this) so each test controls exactly what
 * the "verified" event looks like — or have it throw, to drive the
 * signature/parse-failure branches. The {@code v2.core.account_link.returned}
 * event type isn't covered — its handler calls the live Stripe API
 * ({@code Account.retrieve}), which isn't mockable here.
 */
@ControllerSliceTest(StripeWebhookController.class)
class StripeWebhookControllerTest extends AbstractControllerTest {

    @MockitoBean private StripeConnectService stripeConnectService;
    @MockitoBean private PaymentService paymentService;

    private Event accountUpdatedEvent(String accountId, boolean detailsSubmitted) {
        String json = """
                {
                  "id": "evt_test123",
                  "object": "event",
                  "api_version": "2024-04-10",
                  "created": 1700000000,
                  "type": "account.updated",
                  "livemode": false,
                  "pending_webhooks": 0,
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "account",
                      "details_submitted": %s,
                      "charges_enabled": true
                    }
                  },
                  "request": { "id": null, "idempotency_key": null }
                }
                """.formatted(accountId, detailsSubmitted);
        return Event.GSON.fromJson(json, Event.class);
    }

    private Event unhandledTypeEvent() {
        String json = """
                {
                  "id": "evt_test789",
                  "object": "event",
                  "api_version": "2024-04-10",
                  "created": 1700000000,
                  "type": "customer.created",
                  "livemode": false,
                  "pending_webhooks": 0,
                  "data": { "object": { "id": "cus_test123", "object": "customer" } },
                  "request": { "id": null, "idempotency_key": null }
                }
                """;
        return Event.GSON.fromJson(json, Event.class);
    }

    @Test
    void handleWebhook_accountUpdated_detailsSubmitted_marksOnboardingComplete() throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(accountUpdatedEvent("acct_test123", true));

            mockMvc.perform(post("/stripe/webhook")
                            .header("Stripe-Signature", "t=123,v1=dummy")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("received"));
        }

        verify(stripeConnectService).markOnboardingComplete("acct_test123");
    }

    @Test
    void handleWebhook_accountUpdated_detailsNotSubmitted_doesNotMarkComplete() throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(accountUpdatedEvent("acct_test456", false));

            mockMvc.perform(post("/stripe/webhook")
                            .header("Stripe-Signature", "t=123,v1=dummy")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("received"));
        }

        verify(stripeConnectService, never()).markOnboardingComplete(any());
    }

    @Test
    void handleWebhook_unhandledEventType_returns200() throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(unhandledTypeEvent());

            mockMvc.perform(post("/stripe/webhook")
                            .header("Stripe-Signature", "t=123,v1=dummy")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("received"));
        }

        verify(stripeConnectService, never()).markOnboardingComplete(any());
    }

    private Event paymentIntentEvent(String type, String piId, String extraFields) {
        String json = """
                {
                  "id": "evt_test_pi",
                  "object": "event",
                  "api_version": "2024-04-10",
                  "created": 1700000000,
                  "type": "%s",
                  "livemode": false,
                  "pending_webhooks": 0,
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent"%s
                    }
                  },
                  "request": { "id": null, "idempotency_key": null }
                }
                """.formatted(type, piId, extraFields);
        return Event.GSON.fromJson(json, Event.class);
    }

    private Event chargeRefundedEvent(String chargeId, String paymentIntentId) {
        String json = """
                {
                  "id": "evt_test_charge",
                  "object": "event",
                  "api_version": "2024-04-10",
                  "created": 1700000000,
                  "type": "charge.refunded",
                  "livemode": false,
                  "pending_webhooks": 0,
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "charge",
                      "payment_intent": "%s",
                      "refunded": true
                    }
                  },
                  "request": { "id": null, "idempotency_key": null }
                }
                """.formatted(chargeId, paymentIntentId);
        return Event.GSON.fromJson(json, Event.class);
    }

    @Test
    void handleWebhook_paymentIntentAmountCapturableUpdated_reconciles() throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(paymentIntentEvent("payment_intent.amount_capturable_updated", "pi_test123", ""));

            mockMvc.perform(post("/stripe/webhook")
                            .header("Stripe-Signature", "t=123,v1=dummy")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("received"));
        }

        verify(paymentService).reconcilePaymentIntentAuthorized("pi_test123");
    }

    @Test
    void handleWebhook_paymentIntentSucceeded_reconciles() throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(paymentIntentEvent("payment_intent.succeeded", "pi_test456", ", \"amount_received\": 2599"));

            mockMvc.perform(post("/stripe/webhook")
                            .header("Stripe-Signature", "t=123,v1=dummy")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("received"));
        }

        verify(paymentService).reconcilePaymentIntentCompleted("pi_test456", 2599L);
    }

    @Test
    void handleWebhook_paymentIntentFailed_reconciles() throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(paymentIntentEvent("payment_intent.payment_failed", "pi_test789",
                            ", \"last_payment_error\": {\"message\": \"Your card was declined.\"}"));

            mockMvc.perform(post("/stripe/webhook")
                            .header("Stripe-Signature", "t=123,v1=dummy")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("received"));
        }

        verify(paymentService).reconcilePaymentIntentFailed("pi_test789", "Your card was declined.");
    }

    @Test
    void handleWebhook_chargeRefunded_reconciles() throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(chargeRefundedEvent("ch_test123", "pi_test999"));

            mockMvc.perform(post("/stripe/webhook")
                            .header("Stripe-Signature", "t=123,v1=dummy")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("received"));
        }

        verify(paymentService).reconcileChargeRefunded("pi_test999");
    }

    @Test
    void handleWebhook_missingSignatureHeader_returns400() throws Exception {
        mockMvc.perform(post("/stripe/webhook").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Missing Stripe-Signature header"));

        verifyNoInteractions(stripeConnectService);
    }

    @Test
    void handleWebhook_signatureVerificationFails_returns400() throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("bad signature", "t=123,v1=dummy"));

            mockMvc.perform(post("/stripe/webhook")
                            .header("Stripe-Signature", "t=123,v1=dummy")
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Invalid signature"));
        }

        verifyNoInteractions(stripeConnectService);
    }
}
