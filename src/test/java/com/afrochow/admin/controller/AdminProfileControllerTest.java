package com.afrochow.admin.controller;

import com.afrochow.admin.dto.AdminProfileResponseDto;
import com.afrochow.admin.dto.AdminProfileUpdateRequestDto;
import com.afrochow.admin.service.AdminProfileService;
import com.afrochow.common.enums.AdminAccessLevel;
import com.afrochow.common.enums.Department;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for AdminProfileController.
 *
 * Both endpoints take {@code @AuthenticationPrincipal CustomUserDetails} —
 * {@code getProfile} reads {@code getUsername()}, {@code updateProfile} reads
 * {@code getUserId()} — so both need {@code authenticatedAsPrincipal} with a
 * User that has both fields populated. Class-level
 * {@code @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")} is not exercised
 * in this slice (see ControllerSliceTest javadoc).
 */
@ControllerSliceTest(AdminProfileController.class)
class AdminProfileControllerTest extends AbstractControllerTest {

    @MockitoBean private AdminProfileService adminProfileService;

    private static final String USERNAME = "admin-user";
    private static final Long USER_ID = 42L;

    private User adminUser() {
        return User.builder().userId(USER_ID).username(USERNAME).publicUserId("admin-1").build();
    }

    private AdminProfileResponseDto sampleProfile() {
        return AdminProfileResponseDto.builder()
                .publicUserId("admin-1")
                .department(Department.OPERATIONS)
                .accessLevel(AdminAccessLevel.MANAGER)
                .username(USERNAME)
                .firstName("Ada")
                .lastName("Admin")
                .email("ada@afrochow.com")
                .build();
    }

    @Test
    void getProfile_returns200() throws Exception {
        when(adminProfileService.getProfile(USERNAME)).thenReturn(sampleProfile());

        mockMvc.perform(get("/admin/profile").with(authenticatedAsPrincipal(adminUser(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUserId").value("admin-1"))
                .andExpect(jsonPath("$.data.username").value(USERNAME));
    }

    @Test
    void updateProfile_returns200() throws Exception {
        AdminProfileUpdateRequestDto request = AdminProfileUpdateRequestDto.builder()
                .department("MARKETING")
                .build();
        AdminProfileResponseDto updated = sampleProfile();
        updated.setDepartment(Department.MARKETING);

        when(adminProfileService.updateProfile(eq(USER_ID), any(AdminProfileUpdateRequestDto.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/admin/profile")
                        .with(authenticatedAsPrincipal(adminUser(), "ADMIN"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.department").value("MARKETING"));
    }
}
