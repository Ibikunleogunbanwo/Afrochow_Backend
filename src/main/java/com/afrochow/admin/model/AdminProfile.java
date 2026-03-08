package com.afrochow.admin.model;

import com.afrochow.common.enums.AdminAccessLevel;
import com.afrochow.common.enums.Department;
import com.afrochow.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


@Entity
@Table(name = "admin_profile")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminProfileId;

    // ========== LINK TO USER (ONE-TO-ONE) ==========
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // ========== ADMIN INFORMATION ==========

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Department department;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AdminAccessLevel accessLevel = AdminAccessLevel.MODERATOR;

    @Column(length = 8, nullable = false, unique = true, updatable = false)
    private String employeeId;

    // ========== PERMISSIONS ==========
    // What actions can this admin perform?

    @Column(nullable = false)
    @Builder.Default
    private Boolean canVerifyVendors = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canManageUsers = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canViewReports = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canManagePayments = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canManageCategories = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canResolveDisputes = false;
    // ========== ACTIVITY TRACKING ==========

    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalActionsPerformed = 0;

    // ========== TIMESTAMPS ==========

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ========== HELPER METHODS ==========

    @Transient
    public boolean isSuperAdmin() {
        return accessLevel == AdminAccessLevel.SUPER_ADMIN;
    }

    @Transient
    public boolean hasFullAccess() {
        return canVerifyVendors && canManageUsers &&
                canViewReports && canManagePayments &&
                canManageCategories && canResolveDisputes;
    }

    // Record that admin performed an action
    public void recordAction() {
        this.totalActionsPerformed++;
        this.lastLoginAt = LocalDateTime.now();
    }

    // Grant all permissions (make super admin)
    public void grantFullAccess() {
        this.accessLevel = AdminAccessLevel.SUPER_ADMIN;
        this.canVerifyVendors = true;
        this.canManageUsers = true;
        this.canViewReports = true;
        this.canManagePayments = true;
        this.canManageCategories = true;
        this.canResolveDisputes = true;
    }


    @PrePersist
    private void generateEmployeeId() {
        if (this.employeeId == null) {
            this.employeeId = generate8DigitEmployeeId();
        }
    }

    private String generate8DigitEmployeeId() {
        int min = 10000000;
        int max = 99999999;
        int number = java.util.concurrent.ThreadLocalRandom
                .current()
                .nextInt(min, max + 1);
        return String.valueOf(number);
    }
}

