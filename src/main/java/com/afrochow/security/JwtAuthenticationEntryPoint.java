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

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        // SECURITY: Log detailed error server-side for monitoring
        logger.warn("Authentication failed for request to {} from IP {}: {}",
                request.getRequestURI(),
                SecurityUtils.getClientIP(request),
                authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // SECURITY: Return a generic message to prevent information leakage
        ApiResponse<Object> errorResponse = ApiResponse.unauthorized(GENERIC_AUTH_ERROR);

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}