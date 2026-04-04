package com.afrochow.security;

import com.afrochow.security.Utils.SecurityUtils;
import com.afrochow.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT Authentication Entry Point
 *
 * SECURITY: Returns generic error messages to prevent information leakage
 * - Does not expose specific authentication failure reasons
 * - Logs detailed errors server-side for monitoring
 * - Returns consistent "Authentication failed" message to a client
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);
    private static final String GENERIC_AUTH_ERROR = "Authentication failed. Please check your credentials.";

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint() {
        // Configure ObjectMapper once to properly handle LocalDateTime
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // Paths where unauthenticated requests are expected (e.g. frontend polling before login)
    private static final java.util.List<String> EXPECTED_UNAUTH_PATHS = java.util.List.of(
            "/api/notifications/"
    );

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        // SECURITY: Log detailed error server-side for monitoring.
        // Downgrade to DEBUG for endpoints that are polled before auth is established
        // (e.g. notification polling on page load) to reduce log noise.
        String uri = request.getRequestURI();
        boolean isExpectedUnauthPath = EXPECTED_UNAUTH_PATHS.stream().anyMatch(uri::startsWith);

        if (isExpectedUnauthPath) {
            logger.debug("Unauthenticated request to {} from IP {} (expected pre-auth poll): {}",
                    uri,
                    SecurityUtils.getClientIP(request),
                    authException.getMessage());
        } else {
            logger.warn("Authentication failed for request to {} from IP {}: {}",
                    uri,
                    SecurityUtils.getClientIP(request),
                    authException.getMessage());
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // SECURITY: Return a generic message to prevent information leakage
        ApiResponse<Object> errorResponse = ApiResponse.unauthorized(GENERIC_AUTH_ERROR);

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}