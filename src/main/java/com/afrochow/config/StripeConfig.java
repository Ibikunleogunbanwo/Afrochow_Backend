package com.afrochow.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

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

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }
}