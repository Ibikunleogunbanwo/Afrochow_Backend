package com.afrochow.promotion.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.promotion.dto.PromotionRequestDto;
import com.afrochow.promotion.dto.PromotionResponseDto;
import com.afrochow.promotion.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotions", description = "APIs for managing promotional codes")
public class PromotionController {

    private final PromotionService promotionService;

    // ========== CUSTOMER ENDPOINTS ==========

    @GetMapping
    @PreAuthorize("isAuthenticated()")
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
