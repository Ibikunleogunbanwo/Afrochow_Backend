package com.afrochow.common.exceptions;

import lombok.Getter;

/**
 * Exception thrown when rate limit is exceeded
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long secondsUntilReset;

    public RateLimitExceededException(String message, long secondsUntilReset) {
        super(message);
        this.secondsUntilReset = secondsUntilReset;
    }
}
