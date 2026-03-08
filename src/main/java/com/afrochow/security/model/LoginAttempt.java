package com.afrochow.security.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Login Attempt tracking entity for brute force protection
 *
 * SECURITY: Implements OWASP ASVS V2.2.1 (Anti-Automation)
 * - Tracks failed login attempts by email and IP address
 * - Enforces temporary account lockout after threshold breached
 * - Automatic unlock after lockout period expires
 */
@Entity
@Table(name = "login_attempts",
        indexes = {
                @Index(name = "idx_email", columnList = "email"),
                @Index(name = "idx_ip_address", columnList = "ip_address"),
                @Index(name = "idx_last_attempt", columnList = "last_attempt_time")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email address attempting to authenticate
     * SECURITY: Indexed for fast lookups during authentication
     */
    @Column(nullable = false, length = 255)
    private String email;

    /**
     * IP address of the authentication attempt
     * SECURITY: Used to detect distributed brute force attacks
     */
    @Column(name = "ip_address", length = 45) // IPv6 max length
    private String ipAddress;

    /**
     * Number of consecutive failed login attempts
     * SECURITY: Reset to 0 on successful login
     */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /**
     * Timestamp when the account will be unlocked
     * SECURITY: NULL if not locked, future timestamp if locked
     */
    @Column(name = "lockout_until")
    private Instant lockoutUntil;

    /**
     * Timestamp of the most recent login attempt
     * SECURITY: Used for cleaning up old records
     */
    @Column(name = "last_attempt_time", nullable = false)
    private Instant lastAttemptTime;

    /**
     * User-Agent string from the last attempt
     * SECURITY: Helps identify attack patterns and automation
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Whether this lockout was manually overridden by admin
     * SECURITY: Allows admins to unlock legitimate users
     */
    @Column(name = "admin_override")
    @Builder.Default
    private  Boolean adminOverride = false;



    /**
     * Check if the account is currently locked
     *
     * SECURITY:
     * - Returns true if lockout period has not expired
     * - Admin override immediately unlocks account
     *
     * @return true if account is locked, false otherwise
     */
    @Transient
    public boolean isLocked() {
        if (adminOverride) {
            return false;
        }
        return lockoutUntil != null && Instant.now().isBefore(lockoutUntil);
    }

    /**
     * Get remaining lockout time in seconds
     *
     * @return seconds until unlock, or 0 if not locked
     */
    @Transient
    public long getRemainingLockoutSeconds() {
        if (!isLocked()) {
            return 0;
        }
        return Instant.now().until(lockoutUntil, java.time.temporal.ChronoUnit.SECONDS);
    }

    /**
     * Increment the failed attempt counter
     * SECURITY: Updates timestamp on each call
     */
    public void incrementAttempts() {
        this.attemptCount++;
        this.lastAttemptTime = Instant.now();
    }

    /**
     * Reset attempt counter (called on successful login)
     * SECURITY: Clears lockout state
     */
    public void resetAttempts() {
        this.attemptCount = 0;
        this.lockoutUntil = null;
        this.adminOverride = false;
        this.lastAttemptTime = Instant.now();
    }

    /**
     * Apply account lockout
     *
     * @param lockoutDurationSeconds Duration of lockout in seconds
     */
    public void lockAccount(long lockoutDurationSeconds) {
        this.lockoutUntil = Instant.now().plusSeconds(lockoutDurationSeconds);
        this.lastAttemptTime = Instant.now();
    }
}
