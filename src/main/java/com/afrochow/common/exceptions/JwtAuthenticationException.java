package com.afrochow.common.exceptions;

import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

/**
 * Custom JWT Authentication Exception
 *
 * Base exception for all JWT-related authentication failures.
 * Extends Spring Security's AuthenticationException to integrate
 * with Spring Security's exception handling mechanism.
 */
@Getter
public class JwtAuthenticationException extends AuthenticationException {

    private final JwtErrorCode errorCode;

    public JwtAuthenticationException(String message) {
        super(message);
        this.errorCode = JwtErrorCode.INVALID_TOKEN;
    }

    public JwtAuthenticationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = JwtErrorCode.INVALID_TOKEN;
    }

    public JwtAuthenticationException(JwtErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public JwtAuthenticationException(JwtErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * JWT Error Codes for categorizing authentication failures
     */
    @Getter
    public enum JwtErrorCode {
        INVALID_TOKEN("Invalid token format or signature"),
        EXPIRED_TOKEN("Token has expired"),
        MALFORMED_TOKEN("Token is malformed"),
        UNSUPPORTED_TOKEN("Token type not supported"),
        MISSING_TOKEN("No token provided"),
        MISSING_CLAIMS("Required claims missing from token"),
        ACCOUNT_DISABLED("User account is disabled"),
        ACCOUNT_LOCKED("User account is locked"),
        ACCOUNT_EXPIRED("User account has expired"),
        CREDENTIALS_EXPIRED("User credentials have expired"),
        ROLE_MISMATCH("Token role does not match user role"),
        USER_NOT_FOUND("User not found"),
        DECRYPTION_FAILED("Token decryption failed");

        private final String description;

        JwtErrorCode(String description) {
            this.description = description;
        }

    }
}
