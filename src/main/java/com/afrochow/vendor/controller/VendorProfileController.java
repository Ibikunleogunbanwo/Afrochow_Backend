package com.afrochow.vendor.controller;

import com.afrochow.address.dto.AddressRequestDto;
import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.common.ApiResponse;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.user.model.User;
import com.afrochow.vendor.dto.FoodHandlingCertUploadRequestDto;
import com.afrochow.vendor.dto.VendorProfileResponseDto;
import com.afrochow.vendor.dto.VendorProfileUpdateRequestDto;
import com.afrochow.vendor.service.VendorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Controller for vendor profile management
 *
 * Endpoints:
 * - GET /vendor/profile - Get my vendor profile
 * - PUT /vendor/profile - Update my vendor profile
 * - PUT /vendor/profile/address - Update restaurant address
 * - POST /vendor/profile/logo - Upload restaurant logo
 * - POST /vendor/profile/banner - Upload restaurant banner
 */
@RestController
@RequestMapping("/vendor/profile")
@Tag(name = "Vendor Profile", description = "Vendor profile management endpoints")
public class VendorProfileController {

    private final VendorProfileService vendorProfileService;

    public VendorProfileController(VendorProfileService vendorProfileService) {
        this.vendorProfileService = vendorProfileService;
    }

    /**
     * Get vendor profile
     */
    @GetMapping
    @Operation(summary = "Get profile", description = "Get the authenticated vendor's profile")
    public ResponseEntity<ApiResponse<VendorProfileResponseDto>> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        VendorProfileResponseDto profile = vendorProfileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Vendor profile retrieved successfully", profile));
    }


    /**
     * Update vendor profile
     */
    @PutMapping
    @Operation(summary = "Update profile", description = "Update the authenticated vendor's profile information")
    public ResponseEntity<ApiResponse<VendorProfileResponseDto>> updateProfile(
            @Valid @RequestBody VendorProfileUpdateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        VendorProfileResponseDto updatedProfile = vendorProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Vendor profile updated successfully", updatedProfile));
    }


    /**
     * Update vendor address (restaurant location)
     */
    @PutMapping("/address")
    @Operation(summary = "Update address", description = "Update the restaurant's address")
    public ResponseEntity<ApiResponse<AddressResponseDto>> updateAddress(
            @Valid @RequestBody AddressRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        AddressResponseDto updatedAddress = vendorProfileService.updateAddress(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Address updated successfully", updatedAddress)
        );
    }

    /**
     * Resubmit vendor for admin review after rejection.
     * Sets the vendor back to active (pending) so they appear in the admin's pending queue.
     * Only works if the vendor has not yet been verified.
     */
    @PostMapping("/resubmit")
    @Operation(summary = "Resubmit for review",
               description = "Re-activates a rejected vendor application so it appears in the admin pending queue again")
    public ResponseEntity<ApiResponse<VendorProfileResponseDto>> resubmitForReview(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        VendorProfileResponseDto profile = vendorProfileService.resubmitForReview(userId);
        return ResponseEntity.ok(ApiResponse.success("Application resubmitted for review", profile));
    }

    /**
     * Upload food handling certificate (PROVISIONAL vendors only).
     * Accepts a multipart file (PDF or image) plus certificate metadata as form fields.
     * After upload, the cert enters admin review — status moves to VERIFIED once an admin
     * calls PATCH /admin/vendors/{id}/verify-cert.
     */
    @PostMapping(value = "/food-handling-cert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(
        summary = "Upload food handling certificate",
        description = "Upload your Canadian food handling certificate. Only available when your store is in PROVISIONAL status."
    )
    public ResponseEntity<ApiResponse<VendorProfileResponseDto>> uploadFoodHandlingCert(
            @RequestParam("file") MultipartFile certFile,
            @RequestParam("certNumber") String certNumber,
            @RequestParam("issuingBody") String issuingBody,
            @RequestParam(value = "certExpiry", required = false) String certExpiry,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) throws IOException {

        FoodHandlingCertUploadRequestDto metadata = FoodHandlingCertUploadRequestDto.builder()
                .certNumber(certNumber)
                .issuingBody(issuingBody)
                .certExpiry(certExpiry != null
                        ? java.time.LocalDateTime.parse(certExpiry)
                        : null)
                .build();

        Long userId = userDetails.getUserId();
        VendorProfileResponseDto profile = vendorProfileService
                .uploadFoodHandlingCert(userId, certFile, metadata);

        return ResponseEntity.ok(ApiResponse.success(
                "Food handling certificate uploaded successfully. Pending admin review.", profile));
    }

    /**
     * Upload restaurant image
     */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload vendor image", description = "Upload a vendor's logo or banner")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorProfileResponseDto>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) throws IOException {
        String username = userDetails.getUsername();
        VendorProfileResponseDto profile = vendorProfileService.uploadImage(username, file, type);
        return ResponseEntity.ok(ApiResponse.success(type + " uploaded successfully", profile));
    }

}