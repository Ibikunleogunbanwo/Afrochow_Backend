package com.afrochow.admin.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.Role;
import com.afrochow.security.Services.LoginAttemptService;
import com.afrochow.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
@Tag(name = "Admin User Management", description = "Admin APIs for managing all users")
public class AdminUserManagementController {

    private final UserRepository       userRepository;
    private final LoginAttemptService  loginAttemptService;

    // ========== VIEW USERS ==========

    @GetMapping
    @Operation(summary = "Get all users", description = "Get all users in the system (admin only)")
    public ResponseEntity<ApiResponse<List<UserSummaryDto>>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<UserSummaryDto> summaries = users.stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @GetMapping("/{publicUserId}")
    @Operation(summary = "Get user by ID", description = "Get detailed information about a specific user")
    public ResponseEntity<ApiResponse<UserDetailDto>> getUserById(@PathVariable String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return ResponseEntity.ok(ApiResponse.success(toUserDetail(user)));
    }

    @GetMapping("/role/{role}")
    @Operation(summary = "Get users by role", description = "Get all users with a specific role")
    public ResponseEntity<ApiResponse<List<UserSummaryDto>>> getUsersByRole(@PathVariable Role role) {
        List<User> users = userRepository.findByRole(role);
        List<UserSummaryDto> summaries = users.stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active users", description = "Get all active users")
    public ResponseEntity<ApiResponse<List<UserSummaryDto>>> getActiveUsers() {
        List<User> users = userRepository.findByIsActive(true);
        List<UserSummaryDto> summaries = users.stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @GetMapping("/inactive")
    @Operation(summary = "Get inactive users", description = "Get all inactive/suspended users")
    public ResponseEntity<ApiResponse<List<UserSummaryDto>>> getInactiveUsers() {
        List<User> users = userRepository.findByIsActive(false);
        List<UserSummaryDto> summaries = users.stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(summaries));
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
    @Operation(summary = "Get user statistics", description = "Get system-wide user statistics")
    public ResponseEntity<ApiResponse<UserStats>> getUserStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.findByIsActive(true).size();
        long inactiveUsers = totalUsers - activeUsers;
        long customers = userRepository.findByRole(Role.CUSTOMER).size();
        long vendors = userRepository.findByRole(Role.VENDOR).size();
        long admins = userRepository.findByRole(Role.ADMIN).size();
        long superAdmins = userRepository.findByRole(Role.SUPERADMIN).size();

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

    private UserSummaryDto toUserSummary(User user) {
        return UserSummaryDto.builder()
                .publicUserId(user.getPublicUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .isLocked(loginAttemptService.isAccountLocked(user.getEmail()))
                .createdAt(user.getCreatedAt())
                .build();
    }

    private UserDetailDto toUserDetail(User user) {
        return UserDetailDto.builder()
                .publicUserId(user.getPublicUserId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    // ========== INNER CLASSES ==========

    @lombok.Data
    @lombok.Builder
    public static class UserSummaryDto {
        private String publicUserId;
        private String email;
        private String fullName;
        private Role role;
        private Boolean isActive;
        private boolean isLocked;
        private java.time.LocalDateTime createdAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class UserDetailDto {
        private String publicUserId;
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
        private Role role;
        private Boolean isActive;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
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