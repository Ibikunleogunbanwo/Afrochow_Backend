package com.afrochow.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * Initializes the Stripe SDK once on startup.
 * The secret key is injected from application.properties / environment variable.
 *
 * Add to your application.properties:
 *   stripe.secret.key=${STRIPE_SECRET_KEY}
 *
 * Add to your .env / environment:
 *   STRIPE_SECRET_KEY=sk_test_51TDUzSL0v...
 */
@Configuration
public class StripeConfig {

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${stripe.max-network-retries:2}")
    private int maxNetworkRetries;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @Value("${stripe.connect.required:false}")
    private boolean connectRequired;

    private final Environment environment;

    public StripeConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        // A missing env var already fails Spring's placeholder resolution before this
        // runs, but a *present, empty* STRIPE_SECRET_KEY would otherwise sail through
        // and only surface on the first real charge attempt in production. Fail loudly
        // at startup instead.
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "stripe.secret.key (STRIPE_SECRET_KEY) is not set. Refusing to start — " +
                    "payments cannot function without a valid Stripe API key.");
        }
        if (isProd()) {
            if (!secretKey.startsWith("sk_live_")) {
                throw new IllegalStateException(
                        "Production Stripe configuration must use a live secret key (sk_live_...).");
            }
            if (webhookSecret == null || webhookSecret.isBlank()) {
                throw new IllegalStateException(
                        "Production Stripe configuration must set STRIPE_WEBHOOK_SECRET.");
            }
            if (!connectRequired) {
                throw new IllegalStateException(
                        "Production Stripe configuration must set STRIPE_CONNECT_REQUIRED=true.");
            }
        }
        Stripe.apiKey = secretKey;

        // Enables the Stripe SDK's built-in retry for network-level failures (timeout,
        // connection drop) on requests carrying an idempotency key. On a timeout it's
        // otherwise impossible to tell "Stripe never got the request" apart from
        // "Stripe processed it but we never saw the response" — and a customer-initiated
        // retry mints a NEW idempotency key, which would create a genuine second charge
        // if the original had actually succeeded. This setting makes the SDK itself
        // replay the SAME idempotency key on transient network failures, so Stripe
        // returns the original result instead of processing a duplicate.
        Stripe.setMaxNetworkRetries(maxNetworkRetries);
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
