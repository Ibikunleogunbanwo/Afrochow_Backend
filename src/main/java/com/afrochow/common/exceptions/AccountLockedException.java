package com.afrochow.common.exceptions;

import lombok.Getter;

/**
 * Exception thrown when an account is locked due to excessive failed login attempts
 *
 * SECURITY: This exception provides the remaining lockout time to the user
 */
@Getter
public class AccountLockedException extends RuntimeException {

    private final long remainingLockoutSeconds;

    public AccountLockedException(String message, long remainingLockoutSeconds) {
        super(message);
        this.remainingLockoutSeconds = remainingLockoutSeconds;
    }

    /**
     * Get remaining lockout time in minutes (rounded up)
     */
    public long getRemainingLockoutMinutes() {
        return (remainingLockoutSeconds + 59) / 60;
    }
}
