package com.afrochow.image;

import com.afrochow.common.enums.Role;
import com.afrochow.common.exceptions.ImageNotFoundException;
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
 * Controller-layer test for ImageController.
 *
 * {@code deleteImage} doesn't take {@code @AuthenticationPrincipal} — it
 * calls {@code SecurityUtils.getCurrentUserId()} /
 * {@code getCurrentUserRole()} directly, which read from
 * {@code SecurityContextHolder}. {@code authenticatedAsPrincipal} populates
 * that context, so it works here too even without a controller parameter.
 */
@ControllerSliceTest(ImageController.class)
class ImageControllerTest extends AbstractControllerTest {

    @MockitoBean private ImageUploadService imageUploadService;
    @MockitoBean private ImageOwnershipService imageOwnershipService;

    private static final Long USER_ID = 5L;

    private User customerUser() {
        return User.builder()
                .userId(USER_ID)
                .publicUserId("customer-1")
                .role(Role.CUSTOMER)
                .build();
    }

    @Test
    void serveImage_found_returns200() throws Exception {
        byte[] bytes = "fake-image-bytes".getBytes();
        when(imageUploadService.getImageBytes("vendor/logo.png")).thenReturn(bytes);
        when(imageUploadService.getContentType("vendor/logo.png")).thenReturn("image/png");

        mockMvc.perform(get("/images/{category}/{filename}", "vendor", "logo.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void serveImage_notFound_returns404() throws Exception {
        when(imageUploadService.getImageBytes("vendor/ghost.png"))
                .thenThrow(new ImageNotFoundException("Image not found"));

        mockMvc.perform(get("/images/{category}/{filename}", "vendor", "ghost.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteImage_missingParam_returns400() throws Exception {
        mockMvc.perform(delete("/images"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteImage_blankUrl_returns400() throws Exception {
        mockMvc.perform(delete("/images").param("imageUrl", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteImage_notOwned_returns403() throws Exception {
        when(imageOwnershipService.canDelete(eq("https://cdn.example.com/vendor/other.png"), eq(USER_ID), eq(Role.CUSTOMER)))
                .thenReturn(false);

        mockMvc.perform(delete("/images")
                        .param("imageUrl", "https://cdn.example.com/vendor/other.png")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isForbidden());

        verify(imageUploadService, never()).deleteImage(any());
    }

    @Test
    void deleteImage_owned_returns200() throws Exception {
        when(imageOwnershipService.canDelete(eq("https://cdn.example.com/vendor/mine.png"), eq(USER_ID), eq(Role.CUSTOMER)))
                .thenReturn(true);
        doNothing().when(imageUploadService).deleteImage("https://cdn.example.com/vendor/mine.png");

        mockMvc.perform(delete("/images")
                        .param("imageUrl", "https://cdn.example.com/vendor/mine.png")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteImage_serviceThrows_returns500() throws Exception {
        when(imageOwnershipService.canDelete(eq("https://cdn.example.com/vendor/mine.png"), eq(USER_ID), eq(Role.CUSTOMER)))
                .thenReturn(true);
        doThrow(new RuntimeException("Cloudinary error"))
                .when(imageUploadService).deleteImage("https://cdn.example.com/vendor/mine.png");

        mockMvc.perform(delete("/images")
                        .param("imageUrl", "https://cdn.example.com/vendor/mine.png")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isInternalServerError());
    }
}
