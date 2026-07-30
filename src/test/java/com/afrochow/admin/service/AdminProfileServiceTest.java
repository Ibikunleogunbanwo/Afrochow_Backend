package com.afrochow.admin.service;

import com.afrochow.admin.dto.AdminProfileResponseDto;
import com.afrochow.admin.dto.AdminProfileUpdateRequestDto;
import com.afrochow.admin.model.AdminProfile;
import com.afrochow.admin.repository.AdminProfileRepository;
import com.afrochow.common.enums.AdminAccessLevel;
import com.afrochow.common.enums.Department;
import com.afrochow.common.enums.Role;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminProfileServiceTest {

    @Mock private AdminProfileRepository adminProfileRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private AdminProfileService adminProfileService;

    private User adminUser;
    private AdminProfile adminProfile;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().userId(1L).publicUserId("ADM1").username("adeadmin")
                .email("admin@example.com").firstName("Ade").lastName("Admin")
                .phone("4165551234").role(Role.ADMIN).build();
        adminProfile = AdminProfile.builder().adminProfileId(1L).user(adminUser)
                .department(Department.OPERATIONS).accessLevel(AdminAccessLevel.MODERATOR)
                .employeeId("12345678")
                .canManageUsers(false).canManagePayments(false)
                .canVerifyVendors(false).canViewReports(false)
                .canManageCategories(false).canResolveDisputes(false)
                .build();
        adminUser.setAdminProfile(adminProfile);
    }

    // ========== getProfile ==========

    @Test
    void getProfile_adminUser_returnsMappedDto() {
        when(userRepository.findByUsername("adeadmin")).thenReturn(Optional.of(adminUser));

        AdminProfileResponseDto result = adminProfileService.getProfile("adeadmin");

        assertThat(result.getPublicUserId()).isEqualTo("ADM1");
        assertThat(result.getDepartment()).isEqualTo(Department.OPERATIONS);
        assertThat(result.getEmployeeId()).isEqualTo("12345678");
        assertThat(result.getIsProfileComplete()).isTrue(); // has non-blank phone
        assertThat(result.getIsSuperAdmin()).isFalse();
        assertThat(result.getHasFullAccess()).isFalse();
    }

    @Test
    void getProfile_superAdminUser_derivesFromRoleNotAccessLevel() {
        adminUser.setRole(Role.SUPERADMIN);
        // accessLevel intentionally left at MODERATOR — isSuperAdmin must still be
        // true because it's derived from User.role, not the legacy accessLevel field.
        when(userRepository.findByUsername("adeadmin")).thenReturn(Optional.of(adminUser));

        AdminProfileResponseDto result = adminProfileService.getProfile("adeadmin");

        assertThat(result.getIsSuperAdmin()).isTrue();
        assertThat(result.getHasFullAccess()).isTrue();
    }

    @Test
    void getProfile_incompletePhoneMissing_profileCompleteFalse() {
        adminUser.setPhone(null);
        when(userRepository.findByUsername("adeadmin")).thenReturn(Optional.of(adminUser));

        AdminProfileResponseDto result = adminProfileService.getProfile("adeadmin");

        assertThat(result.getIsProfileComplete()).isFalse();
    }

    @Test
    void getProfile_userNotFound_throwsEntityNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminProfileService.getProfile("ghost"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getProfile_nonAdminUser_throwsIllegalState() {
        User customer = User.builder().userId(2L).username("customer1").role(Role.CUSTOMER).build();
        when(userRepository.findByUsername("customer1")).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> adminProfileService.getProfile("customer1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an admin");
    }

    @Test
    void getProfile_adminWithNoProfile_throwsEntityNotFound() {
        User adminWithoutProfile = User.builder().userId(3L).username("noprofile").role(Role.ADMIN).build();
        when(userRepository.findByUsername("noprofile")).thenReturn(Optional.of(adminWithoutProfile));

        assertThatThrownBy(() -> adminProfileService.getProfile("noprofile"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Admin profile not found");
    }

    // ========== updateProfile ==========

    @Test
    void updateProfile_departmentProvided_updatesDepartment() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        AdminProfileUpdateRequestDto request = AdminProfileUpdateRequestDto.builder()
                .department("FINANCE").build();

        AdminProfileResponseDto result = adminProfileService.updateProfile(1L, request);

        assertThat(result.getDepartment()).isEqualTo(Department.FINANCE);
        verify(adminProfileRepository).save(adminProfile);
    }

    @Test
    void updateProfile_permissionFieldsInRequest_areIgnored_privilegeEscalationGuard() {
        // The request tries to grant itself SUPER_ADMIN + every can* flag — the
        // service must silently ignore all of it. Only `department` is honored.
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        AdminProfileUpdateRequestDto request = AdminProfileUpdateRequestDto.builder()
                .accessLevel(AdminAccessLevel.SUPER_ADMIN)
                .canManageUsers(true).canManagePayments(true)
                .canVerifyVendors(true).canViewReports(true)
                .canManageCategories(true).canResolveDisputes(true)
                .employeeId("99999999")
                .build();

        adminProfileService.updateProfile(1L, request);

        assertThat(adminProfile.getAccessLevel()).isEqualTo(AdminAccessLevel.MODERATOR); // unchanged
        assertThat(adminProfile.getCanManageUsers()).isFalse();
        assertThat(adminProfile.getCanManagePayments()).isFalse();
        assertThat(adminProfile.getCanVerifyVendors()).isFalse();
        assertThat(adminProfile.getCanViewReports()).isFalse();
        assertThat(adminProfile.getCanManageCategories()).isFalse();
        assertThat(adminProfile.getCanResolveDisputes()).isFalse();
        assertThat(adminProfile.getEmployeeId()).isEqualTo("12345678"); // unchanged
    }

    @Test
    void updateProfile_nullDepartment_leavesDepartmentUnchanged() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        AdminProfileUpdateRequestDto request = AdminProfileUpdateRequestDto.builder().build();

        adminProfileService.updateProfile(1L, request);

        assertThat(adminProfile.getDepartment()).isEqualTo(Department.OPERATIONS);
    }

    @Test
    void updateProfile_userNotFound_throwsEntityNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminProfileService.updateProfile(99L,
                AdminProfileUpdateRequestDto.builder().build()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateProfile_nonAdminUser_throwsIllegalState() {
        User customer = User.builder().userId(2L).role(Role.CUSTOMER).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> adminProfileService.updateProfile(2L,
                AdminProfileUpdateRequestDto.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an admin");
    }

    @Test
    void updateProfile_adminWithNoProfile_throwsEntityNotFound() {
        User adminWithoutProfile = User.builder().userId(4L).role(Role.ADMIN).build();
        when(userRepository.findById(4L)).thenReturn(Optional.of(adminWithoutProfile));

        assertThatThrownBy(() -> adminProfileService.updateProfile(4L,
                AdminProfileUpdateRequestDto.builder().build()))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
