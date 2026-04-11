package com.afrochow.customer.controller;
import com.afrochow.common.ApiResponse;
import com.afrochow.customer.dto.CompleteProfileRequestDto;
import com.afrochow.customer.dto.CustomerPasswordUpdate;
import com.afrochow.customer.dto.CustomerUpdateRequestDto;
import com.afrochow.customer.dto.CustomerProfileResponseDto;
import com.afrochow.customer.service.CustomerProfileService;
import com.afrochow.security.model.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Controller for customer profile management
 * Endpoints:
 * - GET /customer/profile - Get my profile
 * - PUT /customer/profile - Update my profile
 * - PUT /customer/password - Update my profile password
 * - POST /customer/profile/image - Upload profile image
 */
@RestController
@RequestMapping("/customer/profile")
@Tag(name = "Customer Profile", description = "Customer profile management endpoints")
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    public CustomerProfileController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    /**
     * Complete profile after Google OAuth sign-in.
     * Called once on first login when isProfileComplete = false.
     */
    @PostMapping("/complete")
    @Operation(
            summary = "Complete Google profile",
            description = "One-time endpoint for Google sign-in users to add phone, address, and username after auto-creation"
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomerProfileResponseDto>> completeProfile(
            @Valid @RequestBody CompleteProfileRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomerProfileResponseDto profile =
                customerProfileService.completeProfile(userDetails.getPublicUserId(), request);

        return ResponseEntity.ok(ApiResponse.success("Profile completed successfully", profile));
    }

    /**
     * Get customer profile
     */

    @GetMapping
    @Operation(
            summary = "Get profile",
            description = "Get the authenticated customer's profile"
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomerProfileResponseDto>> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomerProfileResponseDto profile =
                customerProfileService.getProfile(userDetails.getPublicUserId());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Customer profile retrieved successfully",
                        profile));
    }



    /**
     * Update customer profile
     */
    @PutMapping
    @Operation(
            summary = "Update profile",
            description = "Update the authenticated customer's profile"
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomerProfileResponseDto>> updateProfile(
            @Valid @RequestBody CustomerUpdateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomerProfileResponseDto updatedProfile =
                customerProfileService.updateProfile(userDetails.getPublicUserId(), request);

        return ResponseEntity.ok(
                ApiResponse.success("Customer profile updated successfully", updatedProfile)
        );
    }



    /**
     * Toggle notification preferences
     */
    @PatchMapping("/notifications")
    @Operation(
            summary = "Update notification preference",
            description = "Enable or disable all notifications for the authenticated customer"
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomerProfileResponseDto>> updateNotificationPreference(
            @RequestParam boolean enabled,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomerProfileResponseDto updated =
                customerProfileService.updateNotificationPreference(userDetails.getPublicUserId(), enabled);

        String message = enabled ? "Notifications enabled" : "Notifications disabled";
        return ResponseEntity.ok(ApiResponse.success(message, updated));
    }

    /**
     * Update customer profile password
     */
    @PutMapping("/password")
    @Operation(
            summary = "Update password",
            description = "Change the authenticated customer's password"
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @Valid @RequestBody CustomerPasswordUpdate request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        customerProfileService.updatePassword(userDetails.getPublicUserId(), request);

        return ResponseEntity.ok(
                ApiResponse.success("Password updated successfully")
        );
    }




    /**
     * Upload (or replace) the authenticated customer’s profile image.
     */
    @PostMapping(
            value = "/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Upload profile picture",
            description = "Upload a new profile picture for the currently logged-in customer. The previous image (if any) is automatically deleted."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomerProfileResponseDto>> uploadProfileImage(
            @Parameter(
                    description = "Profile picture (JPEG/PNG recommended, max ? MB)",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestParam("file") MultipartFile file,

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) throws IOException {

        CustomerProfileResponseDto updatedProfile = customerProfileService.uploadProfileImage(file, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Profile image uploaded successfully", updatedProfile));
    }


}
