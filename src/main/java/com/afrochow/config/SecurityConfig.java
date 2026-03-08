package com.afrochow.config;

import com.afrochow.security.Services.CustomUserDetailsService;
import com.afrochow.security.JwtAuthenticationFilter;
import com.afrochow.security.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
 *
 * @author Afrochow Team
 * @version 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:4200}")
    private String allowedOrigins;

    @Value("${security.bcrypt.strength:12}")
    private int bcryptStrength;

    /* ==========================================================
       AUTHENTICATION BEANS
       ========================================================== */

    /**
     * Password encoder bean using BCrypt hashing algorithm.
     * Strength is configurable via application.properties:
     * - Development: 10 rounds (faster)
     * - Production: 12+ rounds (more secure)
     *
     * This MUST be the same encoder used everywhere:
     * - User registration
     * - Password changes
     * - Authentication
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    /**
     * Authentication provider that uses:
     * - CustomUserDetailsService to load user from database
     * - PasswordEncoder to verify passwords
     *
     * This is the "brain" that validates credentials during login.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Authentication manager for handling authentication requests.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }



    /* ==========================================================
       SECURITY FILTER CHAIN
       ========================================================== */

    /**
     * Main security configuration defining:
     * - URL access rules
     * - Security headers
     * - CORS settings
     * - JWT filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF - we're using JWT tokens (stateless)
                .csrf(AbstractHttpConfigurer::disable)

                // Enable CORS with custom configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CRITICAL FIX: Disable SecurityContext persistence for stateless JWT
                .securityContext(AbstractHttpConfigurer::disable)

                // Configure comprehensive security headers
                .headers(this::configureSecurityHeaders)

                // Configure URL-based authorization rules
                .authorizeHttpRequests(auth -> auth
                        // ============================================
                        // ADMIN ENDPOINTS - Require ADMIN role
                        // ============================================
                        .requestMatchers("/auth/register/admin").hasRole("ADMIN")
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMIN")

                        // ============================================
                        // PUBLIC ENDPOINTS - No authentication required
                        // ============================================

                        // Authentication endpoints
                        .requestMatchers(
                                "/auth/register/customer",
                                "/auth/register/vendor",
                                "/images/upload/registration",
                                "/images/vendor_image_registration",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/logout-all/**",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/auth/verify-email",
                                "/auth/resend-verification",
                                "/v1/public/**"
                        ).permitAll()


                        // API Documentation
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()


                        // Health & Monitoring
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()


                        // Public static resources
                        .requestMatchers("/public/**").permitAll()

                        // Platform Statistics - Public endpoint
                        .requestMatchers("/stats/**").permitAll()

                        // Images - GET is public, POST requires authentication
                        .requestMatchers(HttpMethod.GET, "/api/images/**", "/images/**").permitAll()

                        // Public product browsing (GET only)
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

                        // ============================================
                        // CUSTOMER ENDPOINTS - Require CUSTOMER role
                        // ============================================
                        .requestMatchers("/customer/**", "/api/v1/customer/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/customer/profile/image").hasRole("CUSTOMER")

                        // ============================================
                        // VENDOR ENDPOINTS - Require VENDOR role
                        // ============================================
                        .requestMatchers("/vendor/**", "/api/v1/vendor/**").hasRole("VENDOR")

                        // ============================================
                        // ORDER ENDPOINTS - Role-based access
                        // ============================================
                        // Create orders: CUSTOMER or VENDOR
                        .requestMatchers(HttpMethod.POST, "/orders/**", "/api/v1/orders/**")
                        .hasAnyRole("CUSTOMER", "VENDOR")

                        // View orders: CUSTOMER, VENDOR, or ADMIN
                        .requestMatchers(HttpMethod.GET, "/orders/**", "/api/v1/orders/**")
                        .hasAnyRole("CUSTOMER", "VENDOR", "ADMIN")

                        // Update/Delete orders: CUSTOMER or ADMIN
                        .requestMatchers(HttpMethod.PUT, "/orders/**", "/api/v1/orders/**")
                        .hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/orders/**", "/api/v1/orders/**")
                        .hasRole("ADMIN")

                        // ============================================
                        // DEFAULT - All other requests require authentication
                        // ============================================
                        .anyRequest().authenticated()
                )

                // Configure exception handling
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )

                // Stateless session management (JWT-based)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Register authentication provider
                .authenticationProvider(authenticationProvider())

                // Add JWT filter before Spring Security's username/password filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /* ==========================================================
       SECURITY HEADERS CONFIGURATION
       ========================================================== */

    /**
     * Configure comprehensive security headers to protect against common attacks:
     * - HSTS: Force HTTPS
     * - CSP: Prevent XSS
     * - X-Frame-Options: Prevent clickjacking
     * - X-Content-Type-Options: Prevent MIME sniffing
     * - Referrer-Policy: Control referrer information
     * - Permissions-Policy: Restrict browser features
     */
    private void configureSecurityHeaders(
            org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity> headers) {
        headers
                // HTTP Strict Transport Security (HSTS)
                // Forces browser to use HTTPS for 1 year
                .httpStrictTransportSecurity(hsts -> hsts
                        .maxAgeInSeconds(31536000)
                        .includeSubDomains(true)
                        .preload(true)
                )

                // Content Security Policy (CSP)
                // Prevents XSS by controlling resource loading
                .contentSecurityPolicy(csp -> csp
                        .policyDirectives(
                                "default-src 'self'; " +
                                        "script-src 'self'; " +
                                        "style-src 'self'; " +
                                        "img-src 'self' data: https:; " +
                                        "font-src 'self' data:; " +
                                        "connect-src 'self'; " +
                                        "frame-ancestors 'none'; " +
                                        "base-uri 'self'; " +
                                        "form-action 'self'"
                        )
                )

                // Referrer Policy
                // Controls how much referrer information is sent
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )

                // X-Frame-Options
                // Prevents clickjacking by denying iframe embedding
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)

                // X-Content-Type-Options
                // Prevents MIME type sniffing
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())

                // X-XSS-Protection
                // Legacy XSS protection for older browsers
                .xssProtection(xss -> xss
                        .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                )

                // Permissions Policy
                // Restricts browser features
                .addHeaderWriter(new StaticHeadersWriter(
                        "Permissions-Policy",
                        "geolocation=(self), microphone=(), camera=(), payment=(), " +
                                "usb=(), magnetometer=(), accelerometer=(), gyroscope=()"
                ))

                // X-Permitted-Cross-Domain-Policies
                // Prevents Adobe Flash/PDF from loading data
                .addHeaderWriter(new StaticHeadersWriter(
                        "X-Permitted-Cross-Domain-Policies",
                        "none"
                ));
    }




    /* ==========================================================
       CORS CONFIGURATION
       ========================================================== */

    /**
     * CORS configuration for cross-origin requests.
     *
     * Configuration via application.properties:
     *
     * Development (application-dev.properties):
     *   cors.allowed-origins=http://localhost:3000,http://localhost:4200
     *
     * Production (application-prod.properties):
     *   cors.allowed-origins=https://afrochow.com,https://www.afrochow.com
     *
     * Security considerations:
     * - Never use wildcards (*) with credentials
     * - Explicitly list all allowed origins
     * - Restrict to necessary HTTP methods
     * - Limit exposed and allowed headers
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Parse and validate allowed origins
        List<String> origins = parseAllowedOrigins(allowedOrigins);
        configuration.setAllowedOrigins(origins);

        // Allow necessary HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        // Restrict allowed headers to necessary ones
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Origin"
        ));

        // Expose headers that clients need to read
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "X-Total-Count",
                "X-Total-Pages",
                "X-Current-Page"
        ));

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        // Cache preflight requests for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * Parse allowed origins from comma-separated string.
     * Filters out empty values and trims whitespace.
     */
    private List<String> parseAllowedOrigins(String allowedOriginsString) {
        return Arrays.stream(allowedOriginsString.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}