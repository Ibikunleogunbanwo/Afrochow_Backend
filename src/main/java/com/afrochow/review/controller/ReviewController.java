package com.afrochow.review.controller;

import com.afrochow.common.ApiResponse;
import com.afrochow.common.ResponseBuilder;
import com.afrochow.review.dto.ReviewRequestDto;
import com.afrochow.review.dto.ReviewResponseDto;
import com.afrochow.review.service.ReviewService;
import com.afrochow.security.model.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
@Tag(name = "Review Management", description = "APIs for managing restaurant and product reviews")
public class ReviewController {

    private final ReviewService reviewService;

    // ========== PUBLIC ENDPOINTS ==========

    @GetMapping("/vendors/{vendorPublicId}/reviews")
    @Operation(summary = "Get all visible reviews for a vendor",
            description = "Public endpoint - no authentication required")

    public ResponseEntity<ApiResponse<List<ReviewResponseDto>>> getVendorReviews(@PathVariable String vendorPublicId) {
        List<ReviewResponseDto> reviews = reviewService.getVendorReviews(vendorPublicId);
        return ResponseBuilder.ok("Vendor reviews retrieved successfully", reviews);
    }

    @GetMapping("/products/{productPublicId}/reviews")
    @Operation(summary = "Get all visible reviews for a product", description = "Public endpoint - no authentication required")
    public ResponseEntity<ApiResponse<List<ReviewResponseDto>>> getProductReviews(@PathVariable String productPublicId) {
        List<ReviewResponseDto> reviews = reviewService.getProductReviews(productPublicId);
        return ResponseBuilder.ok("Product reviews retrieved successfully", reviews);
    }

    @GetMapping("/vendors/{vendorPublicId}/rating")
    @Operation(summary = "Get vendor's average rating", description = "Public endpoint - no authentication required")
    public ResponseEntity<ApiResponse<Double>> getVendorAverageRating(@PathVariable String vendorPublicId) {
        Double avgRating = reviewService.getVendorAverageRating(vendorPublicId);
        return ResponseBuilder.ok("Vendor rating retrieved successfully", avgRating);
    }

    @GetMapping("/products/{productPublicId}/rating")
    @Operation(summary = "Get product's average rating", description = "Public endpoint - no authentication required")
    public ResponseEntity<ApiResponse<Double>> getProductAverageRating(@PathVariable String productPublicId) {
        Double avgRating = reviewService.getProductAverageRating(productPublicId);
        return ResponseBuilder.ok("Product rating retrieved successfully", avgRating);
    }

    @GetMapping("/vendors/{vendorPublicId}/reviews/filter")
    @Operation(summary = "Get vendor reviews by minimum rating", description = "Filter reviews (e.g., only 4+ star reviews)")
    public ResponseEntity<ApiResponse<List<ReviewResponseDto>>> getReviewsByMinimumRating(
            @PathVariable String vendorPublicId,
            @RequestParam Integer minRating) {
        List<ReviewResponseDto> reviews = reviewService.getReviewsByMinimumRating(vendorPublicId, minRating);
        return ResponseBuilder.ok("Reviews filtered successfully", reviews);
    }

    @PatchMapping("/reviews/{reviewId}/helpful")
    @Operation(summary = "Mark a review as helpful", description = "Anyone can mark a review as helpful")
    public ResponseEntity<ApiResponse<ReviewResponseDto>> markReviewAsHelpful(@PathVariable Long reviewId) {
        ReviewResponseDto review = reviewService.markReviewAsHelpful(reviewId);
        return ResponseBuilder.ok("Review marked as helpful", review);
    }

    // ========== CUSTOMER ENDPOINTS ==========

    @PostMapping("/customer/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create a new review", description = "Customer creates a review for a vendor or product")
    public ResponseEntity<ApiResponse<ReviewResponseDto>> createReview(
            Authentication authentication,
            @Valid @RequestBody ReviewRequestDto request) {
        String userPublicId = authentication.getName();
        ReviewResponseDto review = reviewService.createReview(userPublicId, request);
        return ResponseBuilder.created("Review created successfully", review);
    }

    @PutMapping("/customer/reviews/{reviewId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update a review", description = "Customer updates their own review (within 24 hours)")
    public ResponseEntity<ApiResponse<ReviewResponseDto>> updateReview(
            Authentication authentication,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewRequestDto request) {
        String userPublicId = authentication.getName();
        ReviewResponseDto review = reviewService.updateReview(userPublicId, reviewId, request);
        return ResponseBuilder.ok("Review updated successfully", review);
    }

    @DeleteMapping("/customer/reviews/{reviewId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Delete a review", description = "Customer deletes their own review (within 24 hours)")
    public ResponseEntity<ApiResponse<String>> deleteReview(
            Authentication authentication,
            @PathVariable Long reviewId) {
        String userPublicId = authentication.getName();
        reviewService.deleteReview(userPublicId, reviewId);
        return ResponseBuilder.ok("Review deleted successfully");
    }

    @GetMapping("/customer/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get my reviews", description = "Get all reviews written by the authenticated customer")
    public ResponseEntity<ApiResponse<List<ReviewResponseDto>>> getMyReviews(Authentication authentication) {
        String userPublicId = authentication.getName();
        List<ReviewResponseDto> reviews = reviewService.getMyReviews(userPublicId);
        return ResponseBuilder.ok("Your reviews retrieved successfully", reviews);
    }

    // ========== VENDOR ENDPOINTS ==========

    @GetMapping("/vendor/reviews")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Get all reviews for my restaurant", description = "Vendor views all reviews (including hidden ones)")
    public ResponseEntity<ApiResponse<List<ReviewResponseDto>>> getMyVendorReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String publicUserId = userDetails.getPublicUserId();
        List<ReviewResponseDto> reviews = reviewService.getMyVendorReviews(publicUserId);
        return ResponseBuilder.ok("Your vendor reviews retrieved successfully", reviews);
    }

    @GetMapping("/vendor/reviews/stats")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Get review statistics", description = "Vendor views their review statistics")
    public ResponseEntity<ApiResponse<ReviewService.VendorReviewStats>> getVendorStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String publicUserId = userDetails.getPublicUserId();
        ReviewService.VendorReviewStats stats = reviewService.getVendorStats(publicUserId);
        return ResponseBuilder.ok("Review statistics retrieved successfully", stats);
    }

    // ========== ADMIN ENDPOINTS ==========

    @GetMapping("/admin/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all reviews", description = "Admin views all reviews in the system")
    public ResponseEntity<ApiResponse<List<ReviewResponseDto>>> getAllReviews() {
        List<ReviewResponseDto> reviews = reviewService.getAllReviews();
        return ResponseBuilder.ok("All reviews retrieved successfully", reviews);
    }

    @GetMapping("/admin/reviews/hidden")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get hidden reviews", description = "Admin views only hidden reviews for moderation")
    public ResponseEntity<ApiResponse<List<ReviewResponseDto>>> getHiddenReviews() {
        List<ReviewResponseDto> reviews = reviewService.getHiddenReviews();
        return ResponseBuilder.ok("Hidden reviews retrieved successfully", reviews);
    }

    @PatchMapping("/admin/reviews/{reviewId}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Hide a review", description = "Admin hides a review (moderation)")
    public ResponseEntity<ApiResponse<ReviewResponseDto>> hideReview(@PathVariable Long reviewId) {
        ReviewResponseDto review = reviewService.hideReview(reviewId);
        return ResponseBuilder.ok("Review hidden successfully", review);
    }

    @PatchMapping("/admin/reviews/{reviewId}/show")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Show a review", description = "Admin makes a hidden review visible again")
    public ResponseEntity<ApiResponse<ReviewResponseDto>> showReview(@PathVariable Long reviewId) {
        ReviewResponseDto review = reviewService.showReview(reviewId);
        return ResponseBuilder.ok("Review shown successfully", review);
    }

    @DeleteMapping("/admin/reviews/{reviewId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete any review", description = "Admin permanently deletes a review")
    public ResponseEntity<ApiResponse<String>> adminDeleteReview(@PathVariable Long reviewId) {
        reviewService.adminDeleteReview(reviewId);
        return ResponseBuilder.ok("Review deleted successfully");
    }

    @GetMapping("/admin/reviews/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get review statistics", description = "Admin views system-wide review statistics")
    public ResponseEntity<ApiResponse<ReviewService.AdminReviewStats>> getAdminStats() {
        ReviewService.AdminReviewStats stats = reviewService.getAdminStats();
        return ResponseBuilder.ok("Review statistics retrieved successfully", stats);
    }
}
