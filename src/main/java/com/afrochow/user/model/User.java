package com.afrochow.user.model;

import com.afrochow.admin.model.AdminProfile;
import com.afrochow.common.enums.AuthProvider;
import com.afrochow.common.enums.Role;
import com.afrochow.common.validation.PhoneUtils;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.notification.model.Notification;
import com.afrochow.promotion.model.PromotionUsage;
import com.afrochow.review.model.Review;
import com.afrochow.email.model.EmailVerificationToken;
import com.afrochow.security.model.PasswordResetToken;
import com.afrochow.security.model.RefreshToken;
import com.afrochow.vendor.model.VendorProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_public_user_id", columnList = "publicUserId"),
        @Index(name = "idx_phone", columnList = "phone")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"customerProfile", "vendorProfile", "adminProfile", "reviews"})
public class User {

    /**
     * Safe alphabet for generated IDs; excludes visually ambiguous characters
     * such as 0/O and I/1.
     */
    private static final String ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ID_LENGTH = 8;
    private static final int USERNAME_BASE_MAX_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(unique = true, nullable = true, length = 50)
    private String username;

    @Column(name = "publicUserId", unique = true, nullable = false, updatable = false, length = 16)
    private String publicUserId;

    @Column(unique = true, nullable = false)
    private String email;

    private String profileImageUrl;

    @Column(nullable = true)
    private String password;

    @Column(unique = true, nullable = true)
    private String googleId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(unique = true)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.EMAIL;

    @Builder.Default
    private Boolean isActive = true;

    @Column(nullable = true)
    private LocalDateTime scheduledForDeletionAt;

    @Builder.Default
    private Boolean emailVerified = true;

    @Builder.Default
    @NotNull
    @AssertTrue
    @Schema(description = "Must accept terms and conditions")
    private Boolean acceptTerms = true;

    // Distinguishes demo/seed accounts (see CompleteFinalSeeder) from real
    // registered users. Real registration never sets this true.

    @Column(nullable = false)
    @Builder.Default
    private Boolean isSeedData = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;

    // Profile relationships

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private CustomerProfile customerProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private VendorProfile vendorProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private AdminProfile adminProfile;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Notification> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EmailVerificationToken> emailVerificationTokens = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PasswordResetToken> passwordResetTokens = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PromotionUsage> promotionUsages = new ArrayList<>();

    @PrePersist
    public void onPrePersist() {
        if (publicUserId == null) {
            publicUserId = role.getPrefix() + "-" + generateShortId();
        }

        if (username == null || username.isBlank()) {
            username = generateUniqueUsername();
        }
    }

    // Domain state transitions

    public void scheduleForDeletion() {
        isActive = false;
        scheduledForDeletionAt = LocalDateTime.now();
    }

    public void reactivate() {
        isActive = true;
        scheduledForDeletionAt = null;
    }

    private String generateUniqueUsername() {
        String base = (firstName + lastName)
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");

        if (base.length() < 3) {
            base = base + "user";
        }

        String truncatedBase = base.substring(0, Math.min(base.length(), USERNAME_BASE_MAX_LENGTH));
        int suffix = SECURE_RANDOM.nextInt(9000) + 1000;
        return truncatedBase + suffix;
    }

    private static String generateShortId() {
        StringBuilder id = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            id.append(ID_ALPHABET.charAt(SECURE_RANDOM.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }

    public void setPhone(String phone) {
        this.phone = PhoneUtils.normalize(phone);
    }

    // Derived helpers

    @Transient
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Transient
    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }

    @Transient
    public boolean isVendor() {
        return role == Role.VENDOR;
    }

    @Transient
    public boolean isAdmin() {
        return role == Role.ADMIN || role == Role.SUPERADMIN;
    }
}
