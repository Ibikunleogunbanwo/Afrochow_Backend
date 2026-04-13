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
     * Update admin profile
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

        // Update admin profile fields
        if (request.getDepartment() != null) {
            adminProfile.setDepartment(Department.valueOf(request.getDepartment()));
        }
        if (request.getAccessLevel() != null) {
            adminProfile.setAccessLevel(request.getAccessLevel());
        }
        if (request.getEmployeeId() != null) {
            adminProfile.setEmployeeId(request.getEmployeeId());
        }
        if (request.getCanVerifyVendors() != null) {
            adminProfile.setCanVerifyVendors(request.getCanVerifyVendors());
        }
        if (request.getCanManageUsers() != null) {
            adminProfile.setCanManageUsers(request.getCanManageUsers());
        }
        if (request.getCanViewReports() != null) {
            adminProfile.setCanViewReports(request.getCanViewReports());
        }
        if (request.getCanManagePayments() != null) {
            adminProfile.setCanManagePayments(request.getCanManagePayments());
        }
        if (request.getCanManageCategories() != null) {
            adminProfile.setCanManageCategories(request.getCanManageCategories());
        }
        if (request.getCanResolveDisputes() != null) {
            adminProfile.setCanResolveDisputes(request.getCanResolveDisputes());
        }

        adminProfileRepository.save(adminProfile);

        return toResponseDto(adminProfile);
    }

    // ========== MAPPING METHODS ==========

    private AdminProfileResponseDto toResponseDto(AdminProfile adminProfile) {
        User user = adminProfile.getUser();
        boolean profileComplete = user != null && user.getPhone() != null && !user.getPhone().isBlank();
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
                .createdAt(adminProfile.getCreatedAt())
                .updatedAt(adminProfile.getUpdatedAt())
                .build();
    }
}
