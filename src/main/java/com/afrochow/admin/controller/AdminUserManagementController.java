package com.afrochow.admin.controller;

import com.afrochow.user.model.User;
import com.afrochow.common.enums.Role;
import com.afrochow.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin User Management", description = "Admin APIs for managing all users")
public class AdminUserManagementController {

    private final UserRepository userRepository;

    // ========== VIEW USERS ==========

    @GetMapping
    @Operation(summary = "Get all users", description = "Get all users in the system (admin only)")
    public ResponseEntity<List<UserSummaryDto>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<UserSummaryDto> summaries = users.stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{publicUserId}")
    @Operation(summary = "Get user by ID", description = "Get detailed information about a specific user")
    public ResponseEntity<UserDetailDto> getUserById(@PathVariable String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return ResponseEntity.ok(toUserDetail(user));
    }

    @GetMapping("/role/{role}")
    @Operation(summary = "Get users by role", description = "Get all users with a specific role")
    public ResponseEntity<List<UserSummaryDto>> getUsersByRole(@PathVariable Role role) {
        List<User> users = userRepository.findByRole(role);
        List<UserSummaryDto> summaries = users.stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active users", description = "Get all active users")
    public ResponseEntity<List<UserSummaryDto>> getActiveUsers() {
        List<User> users = userRepository.findByIsActive(true);
        List<UserSummaryDto> summaries = users.stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/inactive")
    @Operation(summary = "Get inactive users", description = "Get all inactive/suspended users")
    public ResponseEntity<List<UserSummaryDto>> getInactiveUsers() {
        List<User> users = userRepository.findByIsActive(false);
        List<UserSummaryDto> summaries = users.stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/search")
    @Operation(summary = "Search users", description = "Search users by name")
    public ResponseEntity<List<UserSummaryDto>> searchUsers(@RequestParam String query) {
        List<User> users = userRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
        List<UserSummaryDto> summaries = users.stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    // ========== MODIFY USERS ==========

    @PatchMapping("/{publicUserId}/activate")
    @Operation(summary = "Activate user", description = "Activate a suspended user account")
    public ResponseEntity<UserSummaryDto> activateUser(@PathVariable String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.setIsActive(true);
        User updatedUser = userRepository.save(user);
        return ResponseEntity.ok(toUserSummary(updatedUser));
    }

    @PatchMapping("/{publicUserId}/deactivate")
    @Operation(summary = "Deactivate user", description = "Suspend/deactivate a user account")
    public ResponseEntity<UserSummaryDto> deactivateUser(@PathVariable String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Prevent admin from deactivating themselves
        if (user.isAdmin()) {
            throw new IllegalStateException("Cannot deactivate admin accounts");
        }

        user.setIsActive(false);
        User updatedUser = userRepository.save(user);
        return ResponseEntity.ok(toUserSummary(updatedUser));
    }

    @PatchMapping("/{publicUserId}/role")
    @Operation(summary = "Change user role", description = "Change a user's role (CUSTOMER, VENDOR, ADMIN)")
    public ResponseEntity<UserSummaryDto> changeUserRole(
            @PathVariable String publicUserId,
            @RequestParam Role newRole) {

        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Prevent changing admin roles
        if (user.isAdmin() || newRole == Role.ADMIN) {
            throw new IllegalStateException("Cannot change admin roles");
        }

        user.setRole(newRole);
        User updatedUser = userRepository.save(user);
        return ResponseEntity.ok(toUserSummary(updatedUser));
    }

    @DeleteMapping("/{publicUserId}")
    @Operation(summary = "Delete user", description = "Permanently delete a user account (use with caution)")
    public ResponseEntity<Void> deleteUser(@PathVariable String publicUserId) {
        User user = userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Prevent deleting admin accounts
        if (user.isAdmin()) {
            throw new IllegalStateException("Cannot delete admin accounts");
        }

        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    // ========== STATISTICS ==========

    @GetMapping("/stats")
    @Operation(summary = "Get user statistics", description = "Get system-wide user statistics")
    public ResponseEntity<UserStats> getUserStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.findByIsActive(true).size();
        long inactiveUsers = totalUsers - activeUsers;
        long customers = userRepository.findByRole(Role.CUSTOMER).size();
        long vendors = userRepository.findByRole(Role.VENDOR).size();
        long admins = userRepository.findByRole(Role.ADMIN).size();

        UserStats stats = UserStats.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .totalCustomers(customers)
                .totalVendors(vendors)
                .totalAdmins(admins)
                .build();

        return ResponseEntity.ok(stats);
    }

    // ========== HELPER METHODS ==========

    private UserSummaryDto toUserSummary(User user) {
        return UserSummaryDto.builder()
                .publicUserId(user.getPublicUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .isActive(user.getIsActive())
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
    }
}
