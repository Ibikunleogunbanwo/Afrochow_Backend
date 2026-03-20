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

/**
 * Security Configuration for Afrochow Application
 *
 * This configuration provides:
 * - JWT-based stateless authentication
 * - Role-based access control (CUSTOMER, VENDOR, ADMIN)
 * - Comprehensive security headers
 * - CORS configuration
 * - BCrypt password encoding
 */
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
     *
     * Without this, the filter runs twice:
     *  1. Before FilterChainProxy (too early — SecurityContext not ready)
     *  2. Inside the security chain (correct position)
     *
     * The first run sets authentication, then FilterChainProxy re-secures
     * the request clearing the context, then AnonymousAuthenticationFilter
     * overwrites with anonymous — causing 401 on every protected endpoint.
     *
     * Setting enabled=false ensures the filter ONLY runs inside the
     * security chain where it is registered via addFilterBefore().
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
                // Disable CSRF — using JWT tokens (stateless)
                .csrf(AbstractHttpConfigurer::disable)

                // Enable CORS with custom configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Configure comprehensive security headers
                .headers(this::configureSecurityHeaders)

                .authorizeHttpRequests(auth -> auth

                        // ── IMAGES — declared first, explicit per-method ──
                        // Must be at the top so Spring Security matches these
                        // before any authenticated() catch-all rules below.
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
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/images",
                                "/images"
                        ).permitAll()

                        // ── ADMIN ─────────────────────────────────────────
                        .requestMatchers("/auth/register/admin").hasRole("ADMIN")
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMIN")

                        // ── PUBLIC ────────────────────────────────────────
                        .requestMatchers(
                                "/auth/register/customer",
                                "/auth/register/vendor",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/logout-all/**",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/auth/verify-email",
                                "/auth/resend-verification",
                                "/v1/public/**",
                                "/public/**"
                        ).permitAll()

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

                        // ── CUSTOMER ──────────────────────────────────────
                        .requestMatchers("/customer/**", "/api/v1/customer/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/customer/profile/image").hasRole("CUSTOMER")

                        // ── VENDOR ────────────────────────────────────────
                        .requestMatchers("/vendor/**", "/api/v1/vendor/**").hasRole("VENDOR")

                        // ── ORDERS ────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/orders/**", "/api/v1/orders/**")
                        .hasAnyRole("CUSTOMER", "VENDOR")
                        .requestMatchers(HttpMethod.GET, "/orders/**", "/api/v1/orders/**")
                        .hasAnyRole("CUSTOMER", "VENDOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/orders/**", "/api/v1/orders/**")
                        .hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/orders/**", "/api/v1/orders/**")
                        .hasRole("ADMIN")

                        // ── DEFAULT — must be last ────────────────────────
                        .anyRequest().authenticated()
                )

                // Exception handling
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )

                // Stateless session management (JWT-based)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Register authentication provider
                .authenticationProvider(authenticationProvider())

                // Place JWT filter just before AnonymousAuthenticationFilter so our
                // authenticated user is never overwritten by the anonymous placeholder.
                .addFilterBefore(jwtAuthenticationFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }

    /* ==========================================================
       SECURITY HEADERS CONFIGURATION
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
                                "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data: https:; " +
                                        "font-src 'self' data:; " +
                                        "connect-src 'self' https://afrochow-backendnew-production.up.railway.app; " +
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