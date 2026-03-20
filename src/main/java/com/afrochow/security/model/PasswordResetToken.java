package com.afrochow.security.model;

import com.afrochow.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.apache.commons.codec.digest.DigestUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Entity
@Table(name = "password_reset_tokens",
        indexes = {
                @Index(name = "idx_token_hash",  columnList = "token_hash"),
                @Index(name = "idx_user_id",     columnList = "user_id"),
                @Index(name = "idx_expiry_date", columnList = "expiry_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * SHA-256 hash of the raw token.
     *
     * bcrypt is intentionally slow and designed for low-entropy secrets like passwords.
     * Reset tokens are 32 random bytes (256-bit entropy) — brute-forcing them is
     * computationally infeasible regardless of hash speed, so bcrypt's slowness
     * provides no security benefit here and prevents direct DB lookup.
     * SHA-256 is fast, indexable, and appropriate for high-entropy tokens.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Transient
    private String transientRawToken; // only used for email link — never persisted

    public boolean isUsed()    { return Boolean.TRUE.equals(used); }
    public boolean isExpired() { return Instant.now().isAfter(expiryDate); }
    public boolean isValid()   { return !isUsed() && !isExpired(); }
    public void markAsUsed()   { this.used = true; }

    /**
     * Hash a raw token consistently — call this on both create and verify sides.
     */
    public static String hashToken(String rawToken) {
        return DigestUtils.sha256Hex(rawToken);
    }

    /**
     * Factory method. PasswordEncoder is no longer needed here.
     */
    public static PasswordResetToken create(User user,
                                            long expirationMinutes,
                                            String ipAddress,
                                            String userAgent) {
        String rawToken = generateSecureToken();

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .expiryDate(Instant.now().plusSeconds(expirationMinutes * 60))
                .used(false)
                .createdAt(Instant.now())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        token.setTransientRawToken(rawToken);
        return token;
    }

    private static String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}