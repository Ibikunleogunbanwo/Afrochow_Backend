package com.afrochow.admin.controller;

import com.afrochow.admin.dto.AdminProfileUpdateRequestDto;
import com.afrochow.admin.dto.AdminProfileResponseDto;
import com.afrochow.admin.service.AdminProfileService;
import com.afrochow.common.response.ApiResponse;
import com.afrochow.security.model.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for admin profile management
 *
 * Endpoints:
 * - GET /admin/profile - Get my admin profile
 * - PUT /admin/profile - Update my admin profile (cosmetic fields only —
 *   permissions/accessLevel are set at admin-creation time by a SUPERADMIN
 *   via /auth/register/admin and are intentionally NOT editable here, to
 *   prevent an admin from self-granting elevated permissions)
 */
@RestController
@RequestMapping("/admin/profile")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
@Tag(name = "Admin Profile", description = "Admin profile management endpoints")
public class AdminProfileController {

    private final AdminProfileService adminProfileService;

    public AdminProfileController(AdminProfileService adminProfileService) {
        this.adminProfileService = adminProfileService;
    }

    @GetMapping
    @Operation(summary = "Get profile", description = "Get the authenticated admin's profile")
    public ResponseEntity<ApiResponse<AdminProfileResponseDto>> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String username = userDetails.getUsername();
        AdminProfileResponseDto profile = adminProfileService.getProfile(username);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping
    @Operation(summary = "Update profile", description = "Update the authenticated admin's profile")
    public ResponseEntity<ApiResponse<AdminProfileResponseDto>> updateProfile(
            @Valid @RequestBody AdminProfileUpdateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        AdminProfileResponseDto profile = adminProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", profile));
    }
}
