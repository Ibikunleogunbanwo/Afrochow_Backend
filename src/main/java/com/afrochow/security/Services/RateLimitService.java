package com.afrochow.security.Services;

import com.afrochow.common.exceptions.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents abuse by limiting how many times an action can be performed within a time window.
 * Example: Only allow 5 registrations per hour from the same IP address.
 */
@Slf4j
@Service
public class RateLimitService {

    // Stores: "login:192.168.1.1" -> AttemptTracker(count=3, startTime=...)
    private final Map<String, AttemptTracker> attempts = new ConcurrentHashMap<>();

    // ============================================
    // Public Methods - Call these to check limits
    // ============================================

    public void verifyRegistrationLimit(String ipAddress) {
        checkLimit(ipAddress, 5, 3600); // 5 attempts per hour
    }

    public void verifyLoginLimit(String compositeKey) {
        checkLimit(compositeKey, 10, 900); // 10 attempts per 15 minutes
    }

    public void verifyPasswordResetLimit(String canonicalIdentifier) {
        checkLimit(canonicalIdentifier, 5, 3600); // 5 attempts per hour
    }

    // ============================================
    // Core Logic - Simple sliding window check
    // ============================================

    private void checkLimit(String identifier, int maxAttempts, int windowSeconds) {
        String key = createKey(identifier);
        Instant now = Instant.now();

        attempts.compute(key, (k, tracker) -> {
            // First attempt - just record it
            if (tracker == null) {
                return new AttemptTracker(1, now);
            }

            long secondsElapsed = now.getEpochSecond() - tracker.startTime.getEpochSecond();

            // Window expired - reset the counter
            if (secondsElapsed >= windowSeconds) {
                return new AttemptTracker(1, now);
            }

            // Too many attempts - reject
            if (tracker.count >= maxAttempts) {
                long secondsLeft = windowSeconds - secondsElapsed;
                String message = String.format(
                        "Too many attempts. Try again in %d seconds.",
                        secondsLeft
                );
                throw new RateLimitExceededException(message, secondsLeft);
            }

            // Still within limits - increment counter
            return new AttemptTracker(tracker.count + 1, tracker.startTime);
        });
    }

    // ============================================
    // Helper Methods
    // ============================================

    private String createKey(String identifier) {
        // Creates keys like "192.168.1.1" (simple, no action prefix needed for basic use)
        return identifier;
    }

    /**
     * Clear tracking for an identifier (useful after successful login, etc.)
     */
    public void resetLimit(String identifier) {
        attempts.remove(createKey(identifier));
    }

    /**
     * Automatic cleanup runs every hour to prevent memory leaks
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(86400); // 24 hours ago
        int before = attempts.size();

        attempts.entrySet().removeIf(entry ->
                entry.getValue().startTime.isBefore(cutoff)
        );

        int removed = before - attempts.size();
        if (removed > 0) {
            log.info("Cleaned up {} old rate limit entries", removed);
        }
    }

    // ============================================
    // Data Structure - Tracks attempts per identifier
    // ============================================

    private record AttemptTracker(int count, Instant startTime) {
    }
}