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
    @Column(nullable = false, length = 20)
    private Department department;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
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

    /**
     * Whether this admin is a real SUPERADMIN.
     *
     * <p>Deliberately derived from {@code User.role} — the field that is actually
     * enforced by every {@code @PreAuthorize}/SecurityConfig check in the app —
     * rather than from {@link #accessLevel}. {@code accessLevel} is a legacy,
     * unenforced categorization (see {@link AdminAccessLevel}) that used to drift
     * out of sync with the real role: an account could show "Super Admin" here
     * while having none of the actual SUPERADMIN privileges (or vice versa after
     * a promote/demote). Promotion to real SUPERADMIN only happens via
     * {@code SuperAdminController.promoteToSuperAdmin}, which sets {@code User.role}
     * directly.
     */
    @Transient
    public boolean isSuperAdmin() {
        return user != null && user.getRole() == com.afrochow.common.enums.Role.SUPERADMIN;
    }

    /**
     * Alias for {@link #isSuperAdmin()} — today "full access" and "SUPERADMIN"
     * are the same thing. The six {@code can*} flags below are legacy/decorative:
     * they're recorded at admin-creation time for record-keeping but are not read
     * by any authorization check, so they deliberately don't factor in here.
     */
    @Transient
    public boolean hasFullAccess() {
        return isSuperAdmin();
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

