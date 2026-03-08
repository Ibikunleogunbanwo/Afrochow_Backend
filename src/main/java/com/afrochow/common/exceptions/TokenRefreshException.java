package com.afrochow.common.exceptions;

/**
 * Exception thrown when refresh token operations fail
 *
 * Used for:
 * - Invalid refresh tokens
 * - Expired refresh tokens
 * - Revoked refresh tokens
 * - Token rotation errors
 * - Token reuse detection
 */
public class TokenRefreshException extends RuntimeException {

    public TokenRefreshException(String message) {
        super(message);
    }

    public TokenRefreshException(String message, Throwable cause) {
        super(message, cause);
    }
}
