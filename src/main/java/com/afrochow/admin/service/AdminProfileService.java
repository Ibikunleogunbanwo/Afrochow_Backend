package com.afrochow.admin.service;

import com.afrochow.admin.dto.AdminProfileUpdateRequestDto;
import com.afrochow.admin.dto.AdminProfileResponseDto;
import com.afrochow.admin.model.AdminProfile;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.Department;
import com.afrochow.admin.repository.AdminProfileRepository;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing admin profiles
 */
@Service
public class AdminProfileService {

    private final AdminProfileRepository adminProfileRepository;
    private final UserRepository userRepository;

    public AdminProfileService(
            AdminProfileRepository adminProfileRepository,
            UserRepository userRepository
    ) {
        this.adminProfileRepository = adminProfileRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get admin profile
     */
    public AdminProfileResponseDto getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isAdmin()) {
            throw new IllegalStateException("User is not an admin");
        }

        AdminProfile adminProfile = user.getAdminProfile();
        if (adminProfile == null) {
            throw new EntityNotFoundException("Admin profile not found");
        }

        return toResponseDto(adminProfile);
    }

    /**
     * Update admin profile — SELF-SERVICE ONLY.
     *
     * <p>Deliberately limited to cosmetic fields (department). {@code accessLevel}
     * and every {@code can*} permission flag are granted once, at admin-creation
     * time, by a SUPERADMIN via {@code /auth/register/admin} — they must never be
     * settable through this endpoint, since it operates on the caller's own
     * profile with no additional privilege check. Previously this method applied
     * every field on the request DTO unconditionally, which let any authenticated
     * admin call PUT /admin/profile on themselves and grant themselves
     * canManageUsers/canManagePayments/canVerifyVendors/canResolveDisputes or
     * bump their own accessLevel — a privilege-escalation bug.
     *
     * <p>If a legitimate need for admin-to-admin permission management shows up
     * later, it should be a separate SUPERADMIN-only endpoint that takes a target
     * {@code publicUserId}, not a change to this one.
     */
    @Transactional
    public AdminProfileResponseDto updateProfile(Long userId, AdminProfileUpdateRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.isAdmin()) {
            throw new IllegalStateException("User is not an admin");
        }

        AdminProfile adminProfile = user.getAdminProfile();
        if (adminProfile == null) {
            throw new EntityNotFoundException("Admin profile not found");
        }

        // Only cosmetic, non-privileged fields may be self-updated.
        if (request.getDepartment() != null) {
            adminProfile.setDepartment(Department.valueOf(request.getDepartment()));
        }

        adminProfileRepository.save(adminProfile);

        return toResponseDto(adminProfile);
    }

    // ========== MAPPING METHODS ==========

    private AdminProfileResponseDto toResponseDto(AdminProfile adminProfile) {
        User user = adminProfile.getUser();
        boolean profileComplete = user != null && user.getPhone() != null && !user.getPhone().isBlank();
        String authProvider = user != null && user.getAuthProvider() != null ? user.getAuthProvider().name() : "EMAIL";
        return AdminProfileResponseDto.builder()
                .publicUserId(user != null ? user.getPublicUserId() : null)
                .department(adminProfile.getDepartment() != null ? adminProfile.getDepartment(): null)
                .accessLevel(adminProfile.getAccessLevel())
                .isSuperAdmin(adminProfile.isSuperAdmin())
                .email(user != null ? user.getEmail() : null)
                .username(user != null ? user.getUsername() : null)
                .firstName(user != null ? user.getFirstName() : null)
                .lastName(user != null ? user.getLastName() : null)
                .phone(user != null ? user.getPhone() : null)
                .profileImageUrl(user != null ? user.getProfileImageUrl() : null)
                .employeeId(adminProfile.getEmployeeId() != null ? adminProfile.getEmployeeId() : null)
                .hasFullAccess(adminProfile.hasFullAccess())
                .isProfileComplete(profileComplete)
                .authProvider(authProvider)
                .createdAt(adminProfile.getCreatedAt())
                .updatedAt(adminProfile.getUpdatedAt())
                .build();
    }
}
