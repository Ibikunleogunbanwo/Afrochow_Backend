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
        return AdminProfileResponseDto.builder()
                .publicUserId(adminProfile.getUser() != null ? adminProfile.getUser().getPublicUserId() : null)
                .department(adminProfile.getDepartment() != null ? adminProfile.getDepartment(): null)
                .accessLevel(adminProfile.getAccessLevel())
                .isSuperAdmin(adminProfile.isSuperAdmin())
                .email(adminProfile.getUser().getEmail())
                .username(adminProfile.getUser().getUsername())
                .firstName(adminProfile.getUser().getFirstName())
                .lastName(adminProfile.getUser().getLastName())
                .phone(adminProfile.getUser().getPhone())
                .profileImageUrl(adminProfile.getUser().getProfileImageUrl())
                .employeeId(adminProfile.getEmployeeId() != null ? adminProfile.getEmployeeId() : null)
                .hasFullAccess(adminProfile.hasFullAccess())
                .createdAt(adminProfile.getCreatedAt())
                .updatedAt(adminProfile.getUpdatedAt())
                .build();
    }
}
