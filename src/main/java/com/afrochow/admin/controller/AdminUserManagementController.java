package com.afrochow.admin.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.Role;
import com.afrochow.common.enums.UserStatus;
import com.afrochow.common.enums.VendorStatus;
import com.afrochow.security.Services.LoginAttemptService;
import com.afrochow.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
@Tag(name = "Admin User Management", description = "Admin APIs for managing all users")
public class AdminUserManagementController {

    private final UserRepository      userRepository;
    private final LoginAttemptService loginAttemptService;
    private final OrderRepository     orderRepository;

    // ========== VIEW USERS ==========

    /**
     * Paginated user list with optional server-side filtering.
     *
     * Query params:
     *   page   (default 0)      — zero-based page number
     *   size   (default 25)     — page size, capped at 100
     *   role   (optional)       — CUSTOMER | VENDOR | ADMIN | SUPERADMIN
     *   active (optional bool)  — true = active only, false = inactive only
     *   q      (optional)       — search by first/last name (min 2 chars)
     *
     * Response includes pagination metadata so the frontend can drive the page controls.
     */
    @GetMapping
    @Operation(summary = "Get users (paginated)", description = "Paginated, filterable user list")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllUsers(
            @RequestParam(defaultValue = "0")   int     page,
            @RequestParam(defaultValue = "25")  int     size,
            @RequestParam(required = false)     Role    role,
            @RequestParam(required = false)     Boolean active,
            @RequestParam(required = false)     String  q) {

        size = Math.min(size, 100); // hard cap — never return more than 100 at once
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<User> userPage;
        if (q != null && q.length() >= 2) {
            // Search trumps other filters — return matching users across all roles/statuses
            List<User> matched = userRepository
                    .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(q, q);
            // Wrap in a slice manually (search result sets are small in practice)
            int start = (int) pageable.getOffset();
            int end   = Math.min(start + size, matched.size());
            List<UserSummaryDto> content = (start >= matched.size())
                    ? List.of()
                    : matched.subList(start, end).stream().map(this::toUserSummary).toList();
            Map<String, Object> body = Map.of(
                    "content",        content,
                    "totalElements",  matched.size(),
                    "totalPages",     (int) Math.ceil((double) matched.size() / size),
                    "page",           page,
                    "size",           size
            );
            return ResponseEntity.ok(ApiResponse.success(body));
        } else if (role != null && active != null) {
            userPage = userRepository.findByRoleAndIsActive(role, active, pageable);
        } else if (role != null) {
            userPage = userRepository.findAllByRole(role, pageable);
        } else if (active != null) {
            userPage = userRepository.findByIsActive(active, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        List<UserSummaryDto> summaries = userPage.getContent().stream()
                .map(this::toUserSummary)
                .toList();

        Map<String, Object> body = Map.of(
                "content",        summaries,
                "totalElements",  userPage.getTotalElements(),
                "totalPages",     userPage.getTotalPages(),
                "page",           userPage.getNumber(),
                "size",           userPage.getSize()
        );
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @GetMapping("/{publicUserId}")
    @Operation(summary = "Get user by ID", description = "Get detailed information about a specific user")
    public ResponseEntity<ApiResponse<UserDetailDto>> getUserById(@PathVariable String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return ResponseEntity.ok(ApiResponse.success(toUserDetail(user)));
    }

    @GetMapping("/role/{role}")
    @Operation(summary = "Get users by role (paginated)", description = "Get users with a specific role — use the main /admin/users?role= endpoint instead")
    public ResponseEntity<ApiResponse<List<UserSummaryDto>>> getUsersByRole(@PathVariable Role role) {
        List<User> users = userRepository.findByRole(role);
        return ResponseEntity.ok(ApiResponse.success(users.stream().map(this::toUserSummary).toList()));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active users (legacy)", description = "Use /admin/users?active=true instead")
    public ResponseEntity<ApiResponse<List<UserSummaryDto>>> getActiveUsers() {
        List<User> users = userRepository.findByIsActive(true);
        return ResponseEntity.ok(ApiResponse.success(users.stream().map(this::toUserSummary).toList()));
    }

    @GetMapping("/inactive")
    @Operation(summary = "Get inactive users (legacy)", description = "Use /admin/users?active=false instead")
    public ResponseEntity<ApiResponse<List<UserSummaryDto>>> getInactiveUsers() {
        List<User> users = userRepository.findByIsActive(false);
        return ResponseEntity.ok(ApiResponse.success(users.stream().map(this::toUserSummary).toList()));
    }

    @GetMapping("/search")
    @Operation(summary = "Search users", description = "Search users by name")
    public ResponseEntity<ApiResponse<List<UserSummaryDto>>> searchUsers(@RequestParam String query) {
        List<User> users = userRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
        List<UserSummaryDto> summaries = users.stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    // ========== MODIFY USERS ==========

    @Transactional
    @PatchMapping("/{publicUserId}/activate")
    @Operation(summary = "Activate user", description = "Activate a suspended user account")
    public ResponseEntity<ApiResponse<UserSummaryDto>> activateUser(@PathVariable String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getRole() == Role.SUPERADMIN) {
            throw new IllegalStateException("Cannot modify a SUPERADMIN account");
        }

        user.setIsActive(true);
        User updatedUser = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("User activated successfully", toUserSummary(updatedUser)));
    }

    @Transactional
    @PatchMapping("/{publicUserId}/deactivate")
    @Operation(summary = "Deactivate user", description = "Suspend/deactivate a user account")
    public ResponseEntity<ApiResponse<UserSummaryDto>> deactivateUser(@PathVariable String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getRole() == Role.SUPERADMIN) {
            throw new IllegalStateException("Cannot modify a SUPERADMIN account");
        }

        if (user.isAdmin()) {
            throw new IllegalStateException("Cannot deactivate admin accounts");
        }

        user.setIsActive(false);
        User updatedUser = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully", toUserSummary(updatedUser)));
    }

    @Transactional
    @PatchMapping("/{publicUserId}/unlock")
    @Operation(summary = "Unlock user account", description = "Clear a login lockout caused by too many failed attempts")
    public ResponseEntity<ApiResponse<UserSummaryDto>> unlockUserAccount(@PathVariable String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getRole() == Role.SUPERADMIN) {
            throw new IllegalStateException("Cannot modify a SUPERADMIN account");
        }

        boolean wasLocked = loginAttemptService.unlockAccount(user.getEmail());
        String message = wasLocked ? "Account unlocked successfully" : "Account was not locked";
        return ResponseEntity.ok(ApiResponse.success(message, toUserSummary(user)));
    }

    @Transactional
    @PatchMapping("/{publicUserId}/role")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Change user role", description = "Change a user's role — SUPERADMIN only")
    public ResponseEntity<ApiResponse<UserSummaryDto>> changeUserRole(
            @PathVariable String publicUserId,
            @RequestParam Role newRole) {

        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Cannot assign SUPERADMIN role via API
        if (newRole == Role.SUPERADMIN) {
            throw new IllegalStateException("Cannot assign SUPERADMIN role via API");
        }

        // Cannot modify a SUPERADMIN
        if (user.getRole() == Role.SUPERADMIN) {
            throw new IllegalStateException("Cannot modify a SUPERADMIN");
        }

        user.setRole(newRole);
        User updatedUser = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("User role updated successfully", toUserSummary(updatedUser)));
    }

    @Transactional
    @DeleteMapping("/{publicUserId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Delete user", description = "Permanently delete a user account — SUPERADMIN only")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Cannot delete a SUPERADMIN
        if (user.getRole() == Role.SUPERADMIN) {
            throw new IllegalStateException("Cannot delete a SUPERADMIN");
        }

        // Cannot delete regular admin accounts either
        if (user.isAdmin()) {
            throw new IllegalStateException("Cannot delete admin accounts");
        }

        userRepository.delete(user);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully"));
    }

    // ========== STATISTICS ==========

    @GetMapping("/stats")
    @Operation(summary = "Get user statistics", description = "Get system-wide user statistics using efficient COUNT queries")
    public ResponseEntity<ApiResponse<UserStats>> getUserStats() {
        // Use COUNT queries — never load full user lists just to call .size()
        long totalUsers    = userRepository.count();
        long customers     = userRepository.countByRole(Role.CUSTOMER);
        long vendors       = userRepository.countByRole(Role.VENDOR);
        long admins        = userRepository.countByRole(Role.ADMIN);
        long superAdmins   = userRepository.countByRole(Role.SUPERADMIN);
        long activeUsers   = userRepository.countByIsActiveTrue();
        long inactiveUsers = totalUsers - activeUsers;

        UserStats stats = UserStats.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .totalCustomers(customers)
                .totalVendors(vendors)
                .totalAdmins(admins)
                .totalSuperAdmins(superAdmins)
                .build();

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // ========== HELPER METHODS ==========

    /**
     * Derives the canonical {@link UserStatus} for a user.
     *
     * Priority (highest → lowest):
     *   1. SUSPENDED  – admin has deactivated the account
     *   2. LOCKED     – too many failed login attempts
     *   3. PENDING_VERIFICATION – email not yet confirmed
     *   4. ACTIVE     – everything is in order
     */
    private UserStatus resolveUserStatus(User user, boolean isLocked) {
        if (Boolean.FALSE.equals(user.getIsActive())) return UserStatus.SUSPENDED;
        if (isLocked)                                   return UserStatus.LOCKED;
        if (Boolean.FALSE.equals(user.getEmailVerified())) return UserStatus.PENDING_VERIFICATION;
        return UserStatus.ACTIVE;
    }

    /**
     * Role-aware profile completeness:
     * <ul>
     *   <li>VENDOR   – profile is considered complete once the vendor has submitted
     *                  it for review (status has advanced past PENDING_PROFILE).</li>
     *   <li>CUSTOMER / ADMIN – complete when a phone number is present (basic
     *                  indicator that the account setup was finished).</li>
     * </ul>
     */
    private boolean resolveProfileComplete(User user, VendorStatus vendorStatus) {
        if (user.getRole() == Role.VENDOR) {
            return vendorStatus != null && vendorStatus != VendorStatus.PENDING_PROFILE;
        }
        return user.getPhone() != null && !user.getPhone().isBlank();
    }

    private String resolveOrderLabel(User user) {
        return switch (user.getRole()) {
            case CUSTOMER   -> "Orders Placed";
            case VENDOR     -> "Orders Fulfilled";
            default         -> null;
        };
    }

    private UserSummaryDto toUserSummary(User user) {
        Long totalOrders = countOrders(user);
        String authProvider = user.getAuthProvider() != null ? user.getAuthProvider().name() : "EMAIL";
        boolean isLocked = loginAttemptService.isAccountLocked(user.getEmail());
        VendorStatus vendorStatus = user.getVendorProfile() != null
                ? user.getVendorProfile().getVendorStatus()
                : null;
        String restaurantName = user.getVendorProfile() != null
                ? user.getVendorProfile().getRestaurantName()
                : null;
        return UserSummaryDto.builder()
                .publicUserId(user.getPublicUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .restaurantName(restaurantName)
                .role(user.getRole())
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .isLocked(isLocked)
                .userStatus(resolveUserStatus(user, isLocked))
                .vendorStatus(vendorStatus)
                .profileImageUrl(user.getProfileImageUrl())
                .totalOrders(totalOrders)
                .orderLabel(resolveOrderLabel(user))
                .createdAt(user.getCreatedAt())
                .isProfileComplete(resolveProfileComplete(user, vendorStatus))
                .authProvider(authProvider)
                .build();
    }

    private UserDetailDto toUserDetail(User user) {
        Long totalOrders = countOrders(user);
        String authProvider = user.getAuthProvider() != null ? user.getAuthProvider().name() : "EMAIL";
        boolean isLocked = loginAttemptService.isAccountLocked(user.getEmail());
        VendorStatus vendorStatus = user.getVendorProfile() != null
                ? user.getVendorProfile().getVendorStatus()
                : null;
        String restaurantName = user.getVendorProfile() != null
                ? user.getVendorProfile().getRestaurantName()
                : null;
        return UserDetailDto.builder()
                .publicUserId(user.getPublicUserId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole())
                .restaurantName(restaurantName)
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .isLocked(isLocked)
                .userStatus(resolveUserStatus(user, isLocked))
                .vendorStatus(vendorStatus)
                .totalOrders(totalOrders)
                .orderLabel(resolveOrderLabel(user))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .isProfileComplete(resolveProfileComplete(user, vendorStatus))
                .authProvider(authProvider)
                .build();
    }

    /**
     * Returns the most meaningful order count for the given user role.
     *
     * <ul>
     *   <li>CUSTOMER — all orders ever placed (including cancelled/refunded).
     *       Gives the admin a complete picture of engagement and any problem patterns.</li>
     *   <li>VENDOR   — only DELIVERED orders. This is the vendor's actual fulfilment
     *       track record, excluding cancellations, refunds, and in-progress orders.</li>
     *   <li>ADMIN    — not applicable; returns null.</li>
     * </ul>
     */
    private Long countOrders(User user) {
        if (user.getRole() == Role.CUSTOMER) {
            CustomerProfile profile = user.getCustomerProfile();
            if (profile == null) return 0L;
            return orderRepository.countByCustomer(profile);
        }
        if (user.getRole() == Role.VENDOR) {
            if (user.getVendorProfile() == null) return 0L;
            return orderRepository.countByVendorAndStatus(
                    user.getVendorProfile(), OrderStatus.DELIVERED);
        }
        return null; // admins don't have orders
    }

    // ========== INNER CLASSES ==========

    @lombok.Data
    @lombok.Builder
    public static class UserSummaryDto {
        private String publicUserId;
        private String email;
        private String fullName;
        /** Store name — non-null only when role == VENDOR. Use instead of fullName for display. */
        private String restaurantName;
        private Role role;
        private Boolean isActive;
        private Boolean emailVerified;
        private boolean isLocked;
        /** Canonical account-level state. */
        private UserStatus userStatus;
        /** Vendor workflow state — non-null only when role == VENDOR. */
        private VendorStatus vendorStatus;
        private String profileImageUrl;
        /** Role-appropriate order count (see countOrders javadoc). Null for admins. */
        private Long totalOrders;
        /** Human-readable label for totalOrders: "Orders Placed" for customers, "Orders Fulfilled" for vendors. */
        private String orderLabel;
        private java.time.LocalDateTime createdAt;
        private Boolean isProfileComplete;
        private String authProvider;
    }

    @lombok.Data
    @lombok.Builder
    public static class UserDetailDto {
        private String publicUserId;
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
        private String profileImageUrl;
        private Role role;
        /** Store name — non-null only when role == VENDOR. Use instead of fullName for display. */
        private String restaurantName;
        private Boolean isActive;
        private Boolean emailVerified;
        private boolean isLocked;
        /** Canonical account-level state. */
        private UserStatus userStatus;
        /** Vendor workflow state — non-null only when role == VENDOR. */
        private VendorStatus vendorStatus;
        /** Role-appropriate order count (see countOrders javadoc). Null for admins. */
        private Long totalOrders;
        /** Human-readable label for totalOrders: "Orders Placed" for customers, "Orders Fulfilled" for vendors. */
        private String orderLabel;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private java.time.LocalDateTime lastLoginAt;
        private Boolean isProfileComplete;
        private String authProvider;
    }

    @lombok.Data
    @lombok.Builder
    public static class UserStats {
        private Long totalUsers;
        private Long activeUsers;
        private Long inactiveUsers;
        private Long totalCustomers;
        private Long totalVendors;
        private Long totalAdmins;
        private Long totalSuperAdmins;  // Added
    }
}