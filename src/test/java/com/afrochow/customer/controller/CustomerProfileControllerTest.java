package com.afrochow.customer.controller;

import com.afrochow.customer.dto.CompleteProfileRequestDto;
import com.afrochow.customer.dto.CustomerPasswordUpdate;
import com.afrochow.customer.dto.CustomerProfileResponseDto;
import com.afrochow.customer.dto.CustomerUpdateRequestDto;
import com.afrochow.customer.service.CustomerProfileService;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for CustomerProfileController.
 *
 * All endpoints take {@code @AuthenticationPrincipal CustomUserDetails},
 * covered via {@code authenticatedAsPrincipal}. {@code @PreAuthorize} is not
 * exercised in this slice (see ControllerSliceTest javadoc).
 */
@ControllerSliceTest(CustomerProfileController.class)
class CustomerProfileControllerTest extends AbstractControllerTest {

    @MockitoBean private CustomerProfileService customerProfileService;

    private static final String PUBLIC_USER_ID = "customer-1";

    private User customerUser() {
        return User.builder().publicUserId(PUBLIC_USER_ID).build();
    }

    private CustomerProfileResponseDto sampleProfile() {
        return CustomerProfileResponseDto.builder()
                .publicUserId(PUBLIC_USER_ID)
                .firstName("Ade")
                .lastName("O")
                .email("ade@example.com")
                .phone("+14035551234")
                .isProfileComplete(true)
                .notificationsEnabled(true)
                .build();
    }

    @Test
    void completeProfile_valid_returns200() throws Exception {
        when(customerProfileService.completeProfile(eq(PUBLIC_USER_ID), any(CompleteProfileRequestDto.class)))
                .thenReturn(sampleProfile());

        CompleteProfileRequestDto request = CompleteProfileRequestDto.builder()
                .phone("+14035551234")
                .build();

        mockMvc.perform(post("/customer/profile/complete")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUserId").value(PUBLIC_USER_ID));
    }

    @Test
    void completeProfile_missingPhone_returns400WithValidationErrors() throws Exception {
        CompleteProfileRequestDto request = CompleteProfileRequestDto.builder().build();

        mockMvc.perform(post("/customer/profile/complete")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(customerProfileService, never()).completeProfile(any(), any());
    }

    @Test
    void getProfile_returns200() throws Exception {
        when(customerProfileService.getProfile(PUBLIC_USER_ID)).thenReturn(sampleProfile());

        mockMvc.perform(get("/customer/profile")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ade@example.com"));
    }

    @Test
    void updateProfile_returns200() throws Exception {
        when(customerProfileService.updateProfile(eq(PUBLIC_USER_ID), any(CustomerUpdateRequestDto.class)))
                .thenReturn(sampleProfile());

        CustomerUpdateRequestDto request = CustomerUpdateRequestDto.builder()
                .firstName("Adebayo")
                .build();

        mockMvc.perform(put("/customer/profile")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateNotificationPreference_returns200() throws Exception {
        CustomerProfileResponseDto disabled = sampleProfile();
        disabled.setNotificationsEnabled(false);
        when(customerProfileService.updateNotificationPreference(PUBLIC_USER_ID, false)).thenReturn(disabled);

        mockMvc.perform(patch("/customer/profile/notifications")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER"))
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationsEnabled").value(false));
    }

    @Test
    void updatePassword_valid_returns200() throws Exception {
        doNothing().when(customerProfileService).updatePassword(eq(PUBLIC_USER_ID), any(CustomerPasswordUpdate.class));

        CustomerPasswordUpdate request = CustomerPasswordUpdate.builder()
                .oldPassword("OldPass1!")
                .newPassword("NewPass1!")
                .confirmNewPassword("NewPass1!")
                .build();

        mockMvc.perform(put("/customer/profile/password")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updatePassword_weakPassword_returns400WithValidationErrors() throws Exception {
        CustomerPasswordUpdate request = CustomerPasswordUpdate.builder()
                .oldPassword("OldPass1!")
                .newPassword("weak")
                .confirmNewPassword("weak")
                .build();

        mockMvc.perform(put("/customer/profile/password")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(customerProfileService, never()).updatePassword(any(), any());
    }

    @Test
    void uploadProfileImage_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(customerProfileService.uploadProfileImage(any(), any(CustomUserDetails.class)))
                .thenReturn(sampleProfile());

        mockMvc.perform(multipart("/customer/profile/image")
                        .file(file)
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUserId").value(PUBLIC_USER_ID));
    }
}
