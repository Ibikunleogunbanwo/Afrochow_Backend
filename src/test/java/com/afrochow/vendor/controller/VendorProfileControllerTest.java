package com.afrochow.vendor.controller;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.common.enums.Province;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import com.afrochow.vendor.dto.FoodHandlingCertUploadRequestDto;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import com.afrochow.vendor.dto.VendorProfileUpdateRequestDto;
import com.afrochow.vendor.service.VendorProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for VendorProfileController.
 *
 * Unlike most controllers in this codebase, these endpoints call
 * {@code userDetails.getUserId()} / {@code getUsername()} rather than
 * {@code getPublicUserId()} — so the {@link User} passed to
 * {@code authenticatedAsPrincipal} needs {@code userId} and {@code username}
 * populated, not just {@code publicUserId}.
 */
@ControllerSliceTest(VendorProfileController.class)
class VendorProfileControllerTest extends AbstractControllerTest {

    @MockitoBean private VendorProfileService vendorProfileService;

    private static final Long USER_ID = 42L;
    private static final String USERNAME = "vendor-user";

    private User vendorUser() {
        return User.builder()
                .userId(USER_ID)
                .username(USERNAME)
                .publicUserId("vendor-1")
                .build();
    }

    private VendorProfileResponseDto sampleProfile() {
        return VendorProfileResponseDto.builder()
                .publicUserId("vendor-1")
                .restaurantName("Mama's Kitchen")
                .offersDelivery(true)
                .offersPickup(true)
                .build();
    }

    @Test
    void getProfile_returns200() throws Exception {
        when(vendorProfileService.getProfile(USER_ID)).thenReturn(sampleProfile());

        mockMvc.perform(get("/vendor/profile")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantName").value("Mama's Kitchen"));
    }

    @Test
    void updateProfile_valid_returns200() throws Exception {
        when(vendorProfileService.updateProfile(eq(USER_ID), any(VendorProfileUpdateRequestDto.class)))
                .thenReturn(sampleProfile());

        VendorProfileUpdateRequestDto request = VendorProfileUpdateRequestDto.builder()
                .restaurantName("Mama's Kitchen 2")
                .preparationTime(30)
                .build();

        mockMvc.perform(put("/vendor/profile")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateProfile_preparationTimeOutOfRange_returns400() throws Exception {
        VendorProfileUpdateRequestDto request = VendorProfileUpdateRequestDto.builder()
                .preparationTime(999)
                .build();

        mockMvc.perform(put("/vendor/profile")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(vendorProfileService, never()).updateProfile(any(), any());
    }

    @Test
    void updateAddress_valid_returns200() throws Exception {
        AddressResponseDto address = AddressResponseDto.builder()
                .publicAddressId("addr-1")
                .addressLine("123 Main St")
                .city("Calgary")
                .province(Province.AB)
                .postalCode("T2N1N4")
                .build();
        when(vendorProfileService.updateAddress(eq(USER_ID), any(AddressRequestDto.class)))
                .thenReturn(address);

        AddressRequestDto request = AddressRequestDto.builder()
                .addressLine("123 Main St")
                .city("Calgary")
                .province(Province.AB)
                .postalCode("T2N1N4")
                .build();

        mockMvc.perform(put("/vendor/profile/address")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("Calgary"));
    }

    @Test
    void updateAddress_invalidPostalCode_returns400() throws Exception {
        AddressRequestDto request = AddressRequestDto.builder()
                .addressLine("123 Main St")
                .city("Calgary")
                .province(Province.AB)
                .postalCode("NOTVALID")
                .build();

        mockMvc.perform(put("/vendor/profile/address")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(vendorProfileService, never()).updateAddress(any(), any());
    }

    @Test
    void resubmitForReview_returns200() throws Exception {
        when(vendorProfileService.resubmitForReview(USER_ID)).thenReturn(sampleProfile());

        mockMvc.perform(post("/vendor/profile/resubmit")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void uploadFoodHandlingCert_valid_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "fake-cert-bytes".getBytes());

        when(vendorProfileService.uploadFoodHandlingCert(
                eq(USER_ID), any(), any(FoodHandlingCertUploadRequestDto.class)))
                .thenReturn(sampleProfile());

        mockMvc.perform(multipart("/vendor/profile/food-handling-cert")
                        .file(file)
                        .param("certNumber", "FS-BC-2024-123456")
                        .param("issuingBody", "FoodSafe BC")
                        .param("certExpiry", "2030-06-15T00:00:00")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void uploadFoodHandlingCert_missingCertNumber_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "fake-cert-bytes".getBytes());

        mockMvc.perform(multipart("/vendor/profile/food-handling-cert")
                        .file(file)
                        .param("issuingBody", "FoodSafe BC")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isBadRequest());

        verify(vendorProfileService, never()).uploadFoodHandlingCert(any(), any(), any());
    }

    @Test
    void uploadImage_valid_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png", "fake-image-bytes".getBytes());

        when(vendorProfileService.uploadImage(eq(USERNAME), any(), eq("logo")))
                .thenReturn(sampleProfile());

        mockMvc.perform(multipart("/vendor/profile/image")
                        .file(file)
                        .param("type", "logo")
                        .with(authenticatedAsPrincipal(vendorUser(), "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
