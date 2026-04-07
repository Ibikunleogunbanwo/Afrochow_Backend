package com.afrochow.config;

import com.resend.Resend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Resend HTTP API client for transactional email.
 *
 * Why HTTP instead of SMTP?
 * Resend's HTTP API uses port 443 (HTTPS) which is always open,
 * making it more reliable than raw SMTP (ports 587/465).
 */
@Configuration
public class ResendConfig {

    @Value("${resend.api-key}")
    private String apiKey;

    @Bean
    public Resend resend() {
        return new Resend(apiKey);
    }
}
