package com.afrochow.promotion.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.promotion.dto.PromotionPreviewRequestDto;
import com.afrochow.promotion.dto.PromotionPreviewResponseDto;
import com.afrochow.promotion.dto.PromotionRequestDto;
import com.afrochow.promotion.dto.PromotionResponseDto;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.promotion.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotions", description = "APIs for managing promotional codes")
public class PromotionController {

    private final PromotionService promotionService;
    private final UserRepository userRepository;

    // ========== CUSTOMER ENDPOINTS ==========

    @GetMapping
    @Operation(summary = "Get active promotions", description = "List all currently active promotions")
    public ResponseEntity<ApiResponse<List<PromotionResponseDto>>> getActivePromotions() {
        return ResponseEntity.ok(ApiResponse.success(promotionService.getActivePromotions()));
    }

    @GetMapping("/vendor/{vendorPublicId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active promotions for vendor",
               description = "List global promotions and vendor-specific promotions for a given vendor")
    public ResponseEntity<ApiResponse<List<PromotionResponseDto>>> getActivePromotionsForVendor(
            @PathVariable String vendorPublicId) {
        return ResponseEntity.ok(ApiResponse.success(
                promotionService.getActivePromotionsForVendor(vendorPublicId)));
    }

    @GetMapping("/validate/{code}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Validate promo code", description = "Check if a promo code is valid and preview the discount")
    public ResponseEntity<ApiResponse<PromotionResponseDto>> validateCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(promotionService.validateCode(code)));
    }

    @PostMapping("/preview")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Preview promo discount",
            description = "Calculate the exact discount for a promo code against a specific subtotal and vendor. Safe to call before checkout — does not record usage.")
    public ResponseEntity<ApiResponse<PromotionPreviewResponseDto>> previewDiscount(
            Authentication authentication,
            @Valid @RequestBody PromotionPreviewRequestDto request) {

        String userPublicId = userRepository.findByUsername(authentication.getName())
                .map(u -> u.getPublicUserId())
                .orElse(null);

        return ResponseEntity.ok(ApiResponse.success(
                promotionService.previewDiscount(request, userPublicId)));
    }

    // ========== VENDOR ENDPOINTS ==========

    @GetMapping("/vendor/mine")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Get my promotions", description = "List all promotions created by the authenticated vendor")
    public ResponseEntity<ApiResponse<List<PromotionResponseDto>>> getMyPromotions(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                promotionService.getVendorOwnPromotions(authentication.getName())));
    }

    @PostMapping("/vendor")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Create vendor promotion", description = "Create a promotion scoped to the authenticated vendor's restaurant")
    public ResponseEntity<ApiResponse<PromotionResponseDto>> createVendorPromotion(
            Authentication authentication,
            @Valid @RequestBody PromotionRequestDto request) {
        PromotionResponseDto created = promotionService.createVendorPromotion(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Promotion created successfully", created));
    }

    @PutMapping("/vendor/{publicPromotionId}")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Update vendor promotion", description = "Update a promotion belonging to the authenticated vendor")
    public ResponseEntity<ApiResponse<PromotionResponseDto>> updateVendorPromotion(
            Authentication authentication,
            @PathVariable String publicPromotionId,
            @Valid @RequestBody PromotionRequestDto request) {
        PromotionResponseDto updated = promotionService.updateVendorPromotion(
                authentication.getName(), publicPromotionId, request);
        return ResponseEntity.ok(ApiResponse.success("Promotion updated successfully", updated));
    }

    @DeleteMapping("/vendor/{publicPromotionId}")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Deactivate vendor promotion", description = "Deactivate a promotion belonging to the authenticated vendor")
    public ResponseEntity<ApiResponse<Void>> deactivateVendorPromotion(
            Authentication authentication,
            @PathVariable String publicPromotionId) {
        promotionService.deactivateVendorPromotion(authentication.getName(), publicPromotionId);
        return ResponseEntity.ok(ApiResponse.success("Promotion deactivated successfully"));
    }

    // ========== ADMIN ENDPOINTS ==========

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create promotion", description = "Create a new promotional code (admin only)")
    public ResponseEntity<ApiResponse<PromotionResponseDto>> createPromotion(
            @Valid @RequestBody PromotionRequestDto request) {
        PromotionResponseDto created = promotionService.createPromotion(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Promotion created successfully", created));
    }

    @PutMapping("/admin/{publicPromotionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update promotion", description = "Update an existing promotion (admin only)")
    public ResponseEntity<ApiResponse<PromotionResponseDto>> updatePromotion(
            @PathVariable String publicPromotionId,
            @Valid @RequestBody PromotionRequestDto request) {
        PromotionResponseDto updated = promotionService.updatePromotion(publicPromotionId, request);
        return ResponseEntity.ok(ApiResponse.success("Promotion updated successfully", updated));
    }

    @DeleteMapping("/admin/{publicPromotionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate promotion", description = "Deactivate a promotion (admin only)")
    public ResponseEntity<ApiResponse<Void>> deactivatePromotion(@PathVariable String publicPromotionId) {
        promotionService.deactivatePromotion(publicPromotionId);
        return ResponseEntity.ok(ApiResponse.success("Promotion deactivated"));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all promotions", description = "List all promotions with usage stats (admin only)")
    public ResponseEntity<ApiResponse<List<PromotionResponseDto>>> getAllPromotions() {
        return ResponseEntity.ok(ApiResponse.success(promotionService.getAllPromotions()));
    }

    @GetMapping("/admin/{publicPromotionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get promotion by ID", description = "Get a single promotion with usage stats (admin only)")
    public ResponseEntity<ApiResponse<PromotionResponseDto>> getPromotion(
            @PathVariable String publicPromotionId) {
        return ResponseEntity.ok(ApiResponse.success(
                promotionService.getPromotionByPublicId(publicPromotionId)));
    }
}
