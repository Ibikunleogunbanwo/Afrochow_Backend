package com.afrochow.security.model;

import com.afrochow.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * RefreshToken entity for JWT token rotation
 *
 * Security Features:
 * - One-time use tokens (revoked after refresh)
 * - Automatic expiration (7 days default)
 * - Token family tracking for rotation detection
 * - User association for revocation on logout
 *
 * OWASP Best Practices:
 * - Implements token rotation to prevent replay attacks
 * - Supports revocation for compromised tokens
 * - Short-lived with configurable expiration
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_token", columnList = "token"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_expiry_date", columnList = "expiryDate")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The actual refresh token value (UUID-based)
     */
    @Column(nullable = false, unique = true, length = 512)
    private String token;

    /**
     * User associated with this refresh token
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refresh_token_user"))
    private User user;

    /**
     * Token expiration timestamp
     */
    @Column(nullable = false)
    private Instant expiryDate;

    /**
     * Whether this token has been revoked
     * Set to true when:
     * - Token is used to refresh (rotation)
     * - User logs out
     * - Security incident detected
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    /**
     * Timestamp when token was created
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when token was revoked (null if not revoked)
     */
    private LocalDateTime revokedAt;

    /**
     * Token family ID for rotation tracking
     * All tokens in the same refresh chain share the same family ID
     * Used to detect token reuse attacks
     */
    @Column(length = 36)
    private String tokenFamily;

    /**
     * IP address from which the token was created (for audit logging)
     */
    @Column(length = 45) // IPv6 max length
    private String ipAddress;

    /**
     * User agent from which the token was created (for audit logging)
     */
    @Column(length = 255)
    private String userAgent;

    /**
     * Check if this refresh token has expired
     */
    @Transient
    public boolean isExpired() {
        return Instant.now().isAfter(expiryDate);
    }

    /**
     * Check if this refresh token is valid (not expired and not revoked)
     */
    @Transient
    public boolean isValid() {
        return !isExpired() && !revoked;
    }

    /**
     * Revoke this token
     */
    public void revoke() {
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
    }
}
