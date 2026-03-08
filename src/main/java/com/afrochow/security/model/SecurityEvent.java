package com.afrochow.security.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Security Event entity for audit logging
 *
 * SECURITY: Implements comprehensive security event logging for:
 * - Failed login attempts
 * - JWT validation failures
 * - Token reuse detection
 * - Account lockouts
 * - Suspicious activity patterns
 */
@Entity
@Table(name = "security_events",
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_email", columnList = "email"),
                @Index(name = "idx_event_type", columnList = "event_type"),
                @Index(name = "idx_event_time", columnList = "event_time"),
                @Index(name = "idx_ip_address", columnList = "ip_address"),
                @Index(name = "idx_severity", columnList = "severity")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User ID associated with the event (nullable for pre-authentication events)
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Email address associated with the event
     */
    @Column(name = "email", length = 255)
    private String email;

    /**
     * Type of security event
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private SecurityEventType eventType;

    /**
     * Severity level of the event
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private SecurityEventSeverity severity;

    /**
     * Detailed description of the event
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * IP address from which the event originated
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User-Agent string from the request
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Request URI that triggered the event
     */
    @Column(name = "request_uri", length = 500)
    private String requestUri;

    /**
     * HTTP method (GET, POST, etc.)
     */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /**
     * Timestamp when the event occurred
     */
    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    /**
     * Additional metadata in JSON format (optional)
     */
    @Column(name = "metadata", length = 2000)
    private String metadata;

    /**
     * Security event types
     */
    public enum SecurityEventType {
        // Authentication events
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        LOGIN_LOCKED,
        LOGOUT,

        // JWT events
        JWT_VALIDATION_FAILED,
        JWT_EXPIRED,
        JWT_MALFORMED,
        JWT_SIGNATURE_INVALID,
        JWT_UNSUPPORTED,

        // Token events
        TOKEN_REFRESH_SUCCESS,
        TOKEN_REFRESH_FAILED,
        TOKEN_REUSE_DETECTED,
        TOKEN_REVOKED,

        // Account events
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        PASSWORD_CHANGED,
        EMAIL_CHANGED,

        // Suspicious activity
        MULTIPLE_FAILED_ATTEMPTS,
        SUSPICIOUS_IP_DETECTED,
        CONCURRENT_SESSION_DETECTED,
        UNUSUAL_ACTIVITY_PATTERN,

        // Security configuration
        SECURITY_CONFIG_CHANGED,
        ADMIN_ACTION_PERFORMED
    }

    /**
     * Security event severity levels
     */
    public enum SecurityEventSeverity {
        INFO,       // Normal operations
        WARNING,    // Potentially suspicious
        ERROR,      // Confirmed security issue
        CRITICAL    // Immediate attention required
    }
}
