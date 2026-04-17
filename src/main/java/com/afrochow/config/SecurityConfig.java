package com.afrochow.config;

import com.afrochow.security.Services.CustomUserDetailsService;
import com.afrochow.security.JwtAuthenticationFilter;
import com.afrochow.security.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Value("${cors.allowed-origins:http://localhost:3000,http://10.0.0.149:3000,http://localhost:4200}")
    private String allowedOrigins;

    @Value("${security.bcrypt.strength:12}")
    private int bcryptStrength;

    /* ==========================================================
       AUTHENTICATION BEANS
       ========================================================== */

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Prevents Spring Boot from auto-registering JwtAuthenticationFilter
     * as a standalone servlet filter OUTSIDE the Spring Security filter chain.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /* ==========================================================
       SECURITY FILTER CHAIN
       ========================================================== */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(this::configureSecurityHeaders)

                .authorizeHttpRequests(auth -> auth

                        // ── CORS PREFLIGHT ────────────────────────────────
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ── IMAGES ────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,
                                "/api/images/**",
                                "/images/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/images/upload/registration",
                                "/api/images/vendor_image_registration",
                                "/images/upload/registration",
                                "/images/vendor_image_registration"
                        ).permitAll()
                        // DELETE requires authentication — only the owning user/vendor/admin
                        // should be able to remove images. Access denied → falls through to anyRequest().authenticated()
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/images",
                                "/images"
                        ).authenticated()

                        // ── API DOCS ──────────────────────────────────────
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        // ── HEALTH & MONITORING ───────────────────────────
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // ── PLATFORM STATS ────────────────────────────────
                        .requestMatchers("/stats/**").permitAll()

                        // ── STRIPE WEBHOOK (Stripe calls this directly, no JWT) ───
                        .requestMatchers(HttpMethod.POST, "/stripe/webhook", "/api/stripe/webhook").permitAll()

                        // ── AUTH ──────────────────────────────────────────
                        .requestMatchers(
                                "/auth/register/customer",
                                "/auth/register/vendor",
                                "/auth/login",
                                "/auth/google",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/auth/verify-email",
                                "/auth/resend-verification",
                                "/v1/public/**",
                                "/public/**"
                        ).permitAll()

                        // ── AUTHENTICATED AUTH OPS ────────────────────────
                        // logout-all must require auth — otherwise anyone who
                        // scrapes a publicUserId can trigger session revocation.
                        // The controller also has @PreAuthorize("isAuthenticated()")
                        // but Security filter chain is the first line of defence.
                        .requestMatchers("/auth/logout-all/**", "/auth/change-password")
                                .authenticated()

                        // ── ADMIN ─────────────────────────────────────────
                        .requestMatchers("/auth/register/admin").hasRole("SUPERADMIN")
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasAnyRole("ADMIN", "SUPERADMIN")

                        // ── PUBLIC BROWSING (GET only) ────────────────────
                        .requestMatchers(HttpMethod.GET,
                                "/products/**",
                                "/vendors/**",
                                "/categories/**",
                                "/reviews/**",
                                "/search/**",
                                "/api/v1/products/**",
                                "/api/v1/vendors/**",
                                "/api/v1/categories/**",
                                "/api/v1/reviews/**"
                        ).permitAll()

                        // ── PROMOTIONS ────────────────────────────────────
                        // IMPORTANT: specific rules MUST come before the broad permitAll below.
                        // Spring Security matches top-to-bottom and stops at first match.

                        // Admin — most restrictive first
                        .requestMatchers("/promotions/admin/**").hasAnyRole("ADMIN", "SUPERADMIN")

                        // Vendor — manage own promotions
                        .requestMatchers(HttpMethod.GET, "/promotions/vendor/mine").hasRole("VENDOR")
                        .requestMatchers(HttpMethod.POST, "/promotions/vendor").hasRole("VENDOR")
                        .requestMatchers(HttpMethod.PUT, "/promotions/vendor/**").hasRole("VENDOR")
                        .requestMatchers(HttpMethod.DELETE, "/promotions/vendor/**").hasRole("VENDOR")

                        // Authenticated — preview discount (needs per-user limit check)
                        .requestMatchers(HttpMethod.POST, "/promotions/preview").authenticated()

                        // Public — browse active promotions (must be last in promotions block)
                        .requestMatchers(HttpMethod.GET,
                                "/promotions",
                                "/promotions/**"
                        ).permitAll()

                        // ── CUSTOMER ──────────────────────────────────────
                        .requestMatchers("/customer/**", "/api/v1/customer/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/customer/profile/image").hasRole("CUSTOMER")

                        // ── VENDOR ────────────────────────────────────────
                        .requestMatchers("/vendor/**", "/api/v1/vendor/**").hasRole("VENDOR")

                        // ── ORDERS ────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST,
                                "/orders/**",
                                "/api/v1/orders/**"
                        ).hasAnyRole("CUSTOMER", "VENDOR")
                        .requestMatchers(HttpMethod.GET,
                                "/orders/**",
                                "/api/v1/orders/**"
                        ).hasAnyRole("CUSTOMER", "VENDOR", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/orders/**",
                                "/api/v1/orders/**"
                        ).hasAnyRole("CUSTOMER", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/orders/**",
                                "/api/v1/orders/**"
                        ).hasAnyRole("ADMIN", "SUPERADMIN")

                        // ── DEFAULT — must be last ────────────────────────
                        .anyRequest().authenticated()
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authenticationProvider(authenticationProvider())

                .addFilterBefore(jwtAuthenticationFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }

    /* ==========================================================
       SECURITY HEADERS
       ========================================================== */

    private void configureSecurityHeaders(
            org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity> headers) {
        headers
                .httpStrictTransportSecurity(hsts -> hsts
                        .maxAgeInSeconds(31536000)
                        .includeSubDomains(true)
                        .preload(true)
                )
                .contentSecurityPolicy(csp -> csp
                        .policyDirectives(
                                // This is a REST API — no inline scripts or eval needed.
                                // unsafe-inline and unsafe-eval are intentionally omitted.
                                "default-src 'self'; " +
                                        "script-src 'self'; " +
                                        "style-src 'self'; " +
                                        "img-src 'self' data: https:; " +
                                        "font-src 'self' data:; " +
                                        "connect-src 'self' https://api.afrochow.ca; " +
                                        "frame-ancestors 'none'; " +
                                        "base-uri 'self'; " +
                                        "form-action 'self'"
                        )
                )
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                .xssProtection(xss -> xss
                        .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                )
                .addHeaderWriter(new StaticHeadersWriter(
                        "Permissions-Policy",
                        "geolocation=(self), microphone=(), camera=(), payment=(), " +
                                "usb=(), magnetometer=(), accelerometer=(), gyroscope=()"
                ))
                .addHeaderWriter(new StaticHeadersWriter(
                        "X-Permitted-Cross-Domain-Policies",
                        "none"
                ));
    }

    /* ==========================================================
       CORS CONFIGURATION
       ========================================================== */

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = parseAllowedOrigins(allowedOrigins);
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Origin"
        ));

        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Set-Cookie",
                "X-Total-Count",
                "X-Total-Pages",
                "X-Current-Page"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    private List<String> parseAllowedOrigins(String allowedOriginsString) {
        return Arrays.stream(allowedOriginsString.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}