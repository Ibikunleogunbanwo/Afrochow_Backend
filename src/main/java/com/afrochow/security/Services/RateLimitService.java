package com.afrochow.security.Services;

import com.afrochow.common.exceptions.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents abuse by limiting how many times an action can be performed
 * within a sliding time window.
 *
 * Limits are configurable per environment via application properties:
 *
 *   application-dev.properties:
 *     rate-limit.registration.max=50
 *     rate-limit.registration.window-seconds=60
 *
 *   application-prod.properties:
 *     rate-limit.registration.max=5
 *     rate-limit.registration.window-seconds=3600
 */
@Slf4j
@Service
public class RateLimitService {

    // ─── Config ───────────────────────────────────────────────────────────────
    // Values fall back to sensible prod defaults if not set in properties.

    @Value("${rate-limit.registration.max:5}")
    private int registrationMax;

    @Value("${rate-limit.registration.window-seconds:3600}")
    private int registrationWindow;

    @Value("${rate-limit.login.max:10}")
    private int loginMax;

    @Value("${rate-limit.login.window-seconds:900}")
    private int loginWindow;

    @Value("${rate-limit.password-reset.max:5}")
    private int passwordResetMax;

    @Value("${rate-limit.password-reset.window-seconds:3600}")
    private int passwordResetWindow;

    // ─── Storage ──────────────────────────────────────────────────────────────
    // Key format: "action:identifier" e.g. "register:192.168.1.1"
    // Using ConcurrentHashMap so multiple threads can read/write safely.

    private final Map<String, AttemptTracker> attempts = new ConcurrentHashMap<>();

    // ─── Public API ───────────────────────────────────────────────────────────

    public void verifyRegistrationLimit(String ipAddress) {
        checkLimit("register", ipAddress, registrationMax, registrationWindow);
    }

    public void verifyLoginLimit(String compositeKey) {
        checkLimit("login", compositeKey, loginMax, loginWindow);
    }

    public void verifyPasswordResetLimit(String canonicalIdentifier) {
        checkLimit("reset", canonicalIdentifier, passwordResetMax, passwordResetWindow);
    }

    /**
     * Clears tracking for an identifier — call this after a successful
     * login or registration so legitimate users start fresh next time.
     */
    public void resetLimit(String action, String identifier) {
        attempts.remove(buildKey(action, identifier));
    }

    // ─── Core Logic ───────────────────────────────────────────────────────────

    private void checkLimit(String action, String identifier, int maxAttempts, int windowSeconds) {
        String key = buildKey(action, identifier);
        Instant now = Instant.now();

        // Step 1: Always record the attempt FIRST inside compute().
        // compute() is atomic — no other thread can interleave between the
        // read and the write. We save the updated count unconditionally so
        // that even rejected requests count against the limit (prevents
        // the race condition where throwing inside compute leaves the counter
        // unchanged and lets attackers hammer the endpoint indefinitely).
        attempts.compute(key, (k, tracker) -> {
            if (tracker == null) {
                return new AttemptTracker(1, now);
            }

            long elapsed = now.getEpochSecond() - tracker.startTime.getEpochSecond();

            // Window has expired — reset to 1 (this request)
            if (elapsed >= windowSeconds) {
                return new AttemptTracker(1, now);
            }

            // Window still active — increment regardless of whether we'll reject
            return new AttemptTracker(tracker.count + 1, tracker.startTime);
        });

        // Step 2: Read back the saved tracker and check if over limit.
        // This happens AFTER compute() so the count is always persisted first.
        AttemptTracker saved = attempts.get(key);
        if (saved != null && saved.count > maxAttempts) {
            long elapsed = now.getEpochSecond() - saved.startTime.getEpochSecond();
            long secondsLeft = Math.max(0, windowSeconds - elapsed);
            String message = String.format(
                    "Too many attempts. Try again in %d seconds.", secondsLeft
            );
            log.warn("Rate limit exceeded — action={} identifier={} secondsLeft={}",
                    action, identifier, secondsLeft);
            throw new RateLimitExceededException(message, secondsLeft);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Prefixes the action so login and registration limits never
     * share a counter even if the identifier (IP address) is the same.
     *
     * e.g. "register:192.168.1.1" vs "login:192.168.1.1"
     */
    private String buildKey(String action, String identifier) {
        return action + ":" + identifier;
    }

    // ─── Scheduled Cleanup ────────────────────────────────────────────────────

    /**
     * Runs every hour to remove stale entries from the map.
     * Entries older than 24 hours are safe to delete because all windows
     * are at most 1 hour — anything older is definitely expired.
     * Without this the map would grow indefinitely in long-running processes.
     */
    @Scheduled(fixedRate = 3_600_000) // every hour in ms
    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(86_400); // 24 hours ago
        int before = attempts.size();

        attempts.entrySet().removeIf(entry ->
                entry.getValue().startTime.isBefore(cutoff)
        );

        int removed = before - attempts.size();
        if (removed > 0) {
            log.info("Rate limit cleanup: removed {} stale entries", removed);
        }
    }

    // ─── Data Structure ───────────────────────────────────────────────────────

    /**
     * Immutable snapshot of attempt count and when the window started.
     * Using a record keeps this concise — records are final and all
     * fields are automatically immutable.
     */
    private record AttemptTracker(int count, Instant startTime) {}
}