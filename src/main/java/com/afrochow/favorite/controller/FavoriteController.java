package com.afrochow.favorite.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.common.enums.FavoriteType;
import com.afrochow.favorite.dto.FavoriteRequestDto;
import com.afrochow.favorite.dto.FavoriteResponseDto;
import com.afrochow.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for managing customer favorites (vendors and products)
 */
@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "Favorite management APIs")
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * Add a favorite (vendor or product)
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Add a favorite", description = "Customer adds a vendor or product to favorites")
    public ResponseEntity<ApiResponse<FavoriteResponseDto>> addFavorite(
            Authentication authentication,
            @Valid @RequestBody FavoriteRequestDto request) {
        String username = authentication.getName();
        FavoriteResponseDto response = favoriteService.addFavorite(username, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Favorite added successfully", response));
    }

    /**
     * Remove a favorite (vendor or product)
     */
    @DeleteMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Remove a favorite", description = "Customer removes a vendor or product from favorites")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            Authentication authentication,
            @Valid @RequestBody FavoriteRequestDto request) {
        String username = authentication.getName();
        favoriteService.removeFavorite(username, request);
        return ResponseEntity.ok(ApiResponse.success("Favorite removed successfully", null));
    }

    /**
     * Get all favorites for the logged-in customer
     */
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get all favorites", description = "Get all favorites (vendors and products) for the customer")
    public ResponseEntity<ApiResponse<List<FavoriteResponseDto>>> getAllFavorites(Authentication authentication) {
        String username = authentication.getName();
        List<FavoriteResponseDto> favorites = favoriteService.getAllFavorites(username);
        return ResponseEntity.ok(ApiResponse.success("Favorites retrieved successfully", favorites));
    }

    /**
     * Get favorites by type (VENDOR or PRODUCT)
     */
    @GetMapping("/type/{favoriteType}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get favorites by type", description = "Get all vendor favorites or all product favorites")
    public ResponseEntity<ApiResponse<List<FavoriteResponseDto>>> getFavoritesByType(
            Authentication authentication,
            @PathVariable FavoriteType favoriteType) {
        String username = authentication.getName();
        List<FavoriteResponseDto> favorites = favoriteService.getFavoritesByType(username, favoriteType);
        return ResponseEntity.ok(ApiResponse.success("Favorites retrieved successfully", favorites));
    }

    /**
     * Check if a vendor is favorited
     */
    @GetMapping("/vendor/{vendorPublicId}/is-favorited")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Check if vendor is favorited", description = "Check if the customer has favorited a specific vendor")
    public ResponseEntity<ApiResponse<Boolean>> isVendorFavorited(
            Authentication authentication,
            @PathVariable String vendorPublicId) {
        String username = authentication.getName();
        boolean isFavorited = favoriteService.isVendorFavorited(username, vendorPublicId);
        return ResponseEntity.ok(ApiResponse.success("Favorite status retrieved successfully", isFavorited));
    }

    /**
     * Check if a product is favorited
     */
    @GetMapping("/product/{productPublicId}/is-favorited")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Check if product is favorited", description = "Check if the customer has favorited a specific product")
    public ResponseEntity<ApiResponse<Boolean>> isProductFavorited(
            Authentication authentication,
            @PathVariable String productPublicId) {
        String username = authentication.getName();
        boolean isFavorited = favoriteService.isProductFavorited(username, productPublicId);
        return ResponseEntity.ok(ApiResponse.success("Favorite status retrieved successfully", isFavorited));
    }

    /**
     * Get vendor's total favorite count (public endpoint)
     */
    @GetMapping("/vendor/{vendorPublicId}/count")
    @Operation(summary = "Get vendor favorite count", description = "Get total number of customers who favorited this vendor")
    public ResponseEntity<ApiResponse<Long>> getVendorFavoriteCount(@PathVariable String vendorPublicId) {
        Long count = favoriteService.getVendorFavoriteCount(vendorPublicId);
        return ResponseEntity.ok(ApiResponse.success("Vendor favorite count retrieved successfully", count));
    }

    /**
     * Get product's total favorite count (public endpoint)
     */
    @GetMapping("/product/{productPublicId}/count")
    @Operation(summary = "Get product favorite count", description = "Get total number of customers who favorited this product")
    public ResponseEntity<ApiResponse<Long>> getProductFavoriteCount(@PathVariable String productPublicId) {
        Long count = favoriteService.getProductFavoriteCount(productPublicId);
        return ResponseEntity.ok(ApiResponse.success("Product favorite count retrieved successfully", count));
    }

    /**
     * Get customer's total favorite count
     */
    @GetMapping("/my-count")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get my favorite count", description = "Get total number of favorites for the logged-in customer")
    public ResponseEntity<ApiResponse<Long>> getMyFavoriteCount(Authentication authentication) {
        String username = authentication.getName();
        Long count = favoriteService.getCustomerFavoriteCount(username);
        return ResponseEntity.ok(ApiResponse.success("Favorite count retrieved successfully", count));
    }
}
