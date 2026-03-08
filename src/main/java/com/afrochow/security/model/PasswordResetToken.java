package com.afrochow.security.model;

import com.afrochow.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Base64;
import java.security.SecureRandom;

@Entity
@Table(name = "password_reset_tokens",
        indexes = {
                @Index(name = "idx_token_hash", columnList = "token_hash"),
                @Index(name = "idx_user_id", columnList = "user_id"),
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

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
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
    private String transientRawToken; // only for email

    public boolean isUsed() {
        return Boolean.TRUE.equals(used);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiryDate);
    }

    public boolean isValid() {
        return !isUsed() && !isExpired();
    }

    public void markAsUsed() {
        this.used = true;
    }

    /**
     * Factory method to create a hashed token
     */
    public static PasswordResetToken create(User user,
                                            long expirationMinutes,
                                            String ipAddress,
                                            String userAgent,
                                            PasswordEncoder passwordEncoder) {
        String rawToken = generateSecureToken();
        String hashedToken = passwordEncoder.encode(rawToken);

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hashedToken)
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
