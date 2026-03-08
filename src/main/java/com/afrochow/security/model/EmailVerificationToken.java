package com.afrochow.security.model;

import com.afrochow.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * Email Verification Token for confirming user email addresses
 */
@Entity
@Table(name = "email_verification_token", indexes = {
        @Index(name = "idx_token", columnList = "token"),
        @Index(name = "idx_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tokenId;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    private Instant verifiedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Create a new email verification token
     */
    public static EmailVerificationToken create(User user, long expirationMinutes) {
        return EmailVerificationToken.builder()
                .token(String.format("%06d", new Random().nextInt(1_000_000)))
                .user(user)
                .expiresAt(Instant.now().plusSeconds(expirationMinutes * 60))
                .isUsed(false)
                .build();
    }

    /**
     * Check if the token is expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Mark token as used
     */
    public void markAsUsed() {
        this.isUsed = true;
        this.verifiedAt = Instant.now();
    }
}
