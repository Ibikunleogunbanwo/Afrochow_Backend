package com.afrochow.user.model;

import com.afrochow.admin.model.AdminProfile;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.notification.model.Notification;
import com.afrochow.promotion.model.PromotionUsage;
import com.afrochow.review.model.Review;
import com.afrochow.security.model.EmailVerificationToken;
import com.afrochow.security.model.PasswordResetToken;
import com.afrochow.security.model.RefreshToken;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.common.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;

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

    @Column(nullable = false)
    private String password;

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

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ===== PROFILE RELATIONSHIPS =====
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

    // ===== ID GENERATION =====

    /**
     * Safe alphabet for generated IDs — excludes visually ambiguous characters (0, O, I, 1).
     * 32 characters → 32^8 ≈ 1 trillion unique combinations per role prefix.
     */
    private static final String ID_ALPHABET  = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int    ID_LENGTH    = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static String generateShortId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ID_ALPHABET.charAt(SECURE_RANDOM.nextInt(ID_ALPHABET.length())));
        }
        return sb.toString();
    }

    // ===== AUTO-GENERATE PUBLIC ID & USERNAME =====
    @PrePersist
    public void onPrePersist() {
        if (publicUserId == null) {
            // e.g. CUS-X7MK2P4N, VEN-X7MK2P4N
            publicUserId = role.getPrefix() + "-" + generateShortId();
        }

        if (username == null || username.isBlank()) {
            username = generateUniqueUsername();
        }
    }

    private String generateUniqueUsername() {
        String base = (firstName + lastName)
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");

        if (base.length() < 3) {
            base = base + "user";
        }

        // Truncate base to 16 chars, then append 4 random digits (1000–9999).
        // Total max length = 20 chars. Always exactly 4 digit suffix for readability.
        String truncatedBase = base.substring(0, Math.min(base.length(), 16));
        int suffix = SECURE_RANDOM.nextInt(9000) + 1000;
        return truncatedBase + suffix;
    }

    // ===== PHONE NORMALIZATION =====
    public void setPhone(String phone) {
        if (phone == null) {
            this.phone = null;
            return;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("1")) {
            digits = digits.substring(1);
        }
        this.phone = digits;
    }

    // ===== HELPER METHODS =====
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

    @Transient
    public boolean isSuperAdmin() {
        return role == Role.SUPERADMIN;
    }

    @Transient
    public Object getActiveProfile() {
        return switch (role) {
            case CUSTOMER -> customerProfile;
            case VENDOR -> vendorProfile;
            case ADMIN, SUPERADMIN -> adminProfile;
        };
    }
}