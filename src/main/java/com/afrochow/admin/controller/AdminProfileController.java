package com.afrochow.admin.controller;

import com.afrochow.admin.dto.AdminProfileUpdateRequestDto;
import com.afrochow.admin.dto.AdminProfileResponseDto;
import com.afrochow.admin.service.AdminProfileService;
import com.afrochow.security.model.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for admin profile management
 *
 * Endpoints:
 * - GET /admin/profile - Get my admin profile
 * - PUT /admin/profile - Update my admin profile
 */
@RestController
@RequestMapping("/admin/profile")
@Tag(name = "Admin Profile", description = "Admin profile management endpoints")
public class AdminProfileController {

    private final AdminProfileService adminProfileService;

    public AdminProfileController(AdminProfileService adminProfileService) {
        this.adminProfileService = adminProfileService;
    }

    /**
     * Get admin profile
     */
    @GetMapping
    @Operation(summary = "Get profile", description = "Get the authenticated admin's profile")
    public ResponseEntity<AdminProfileResponseDto> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String username = userDetails.getUsername();
        AdminProfileResponseDto profile = adminProfileService.getProfile(username);
        return ResponseEntity.ok(profile);
    }

    /**
     * Update admin profile
     */
    @PutMapping
    @Operation(summary = "Update profile", description = "Update the authenticated admin's profile")
    public ResponseEntity<AdminProfileResponseDto> updateProfile(
            @Valid @RequestBody AdminProfileUpdateRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        AdminProfileResponseDto profile = adminProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(profile);
    }
}
