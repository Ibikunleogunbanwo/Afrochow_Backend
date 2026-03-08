package com.afrochow.user.model;
import com.afrochow.admin.model.AdminProfile;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.review.model.Review;
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
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_public_user_id", columnList = "publicUserId"),
        @Index(name = "idx_phone", columnList = "phone")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

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
    @Column(nullable = false)
    private Role role;

    @Builder.Default
    private Boolean isActive = true;

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

    // ===== AUTO-GENERATE PUBLIC ID & USERNAME =====
    @PrePersist
    public void onPrePersist() {
        if (publicUserId == null) {
            publicUserId = role.getPrefix() + "-" +
                    UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String combined = base + suffix;

        return combined.substring(0, Math.min(combined.length(), 50));
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
        return role == Role.ADMIN;
    }

    @Transient
    public Object getActiveProfile() {
        return switch (role) {
            case CUSTOMER -> customerProfile;
            case VENDOR -> vendorProfile;
            case ADMIN -> adminProfile;
        };
    }
}
