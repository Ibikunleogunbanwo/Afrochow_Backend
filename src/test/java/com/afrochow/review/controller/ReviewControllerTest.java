package com.afrochow.review.controller;

import com.afrochow.review.dto.ReviewRequestDto;
import com.afrochow.review.dto.ReviewResponseDto;
import com.afrochow.review.service.ReviewService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for ReviewController.
 *
 * See CategoryControllerTest / AbstractControllerTest / ControllerSliceTest
 * for the shared @WebMvcTest slice setup. Customer endpoints here take a
 * plain {@code Authentication} parameter (covered via {@code authenticatedAs}),
 * but the two vendor endpoints use {@code @AuthenticationPrincipal
 * CustomUserDetails} instead — that needs {@code authenticatedAsPrincipal}
 * (see AbstractControllerTest javadoc for why these two resolve differently).
 * @PreAuthorize/@deptAccess enforcement is not exercised in this slice (see
 * ControllerSliceTest javadoc).
 */
@ControllerSliceTest(ReviewController.class)
class ReviewControllerTest extends AbstractControllerTest {

    @MockitoBean private ReviewService reviewService;

    private static final String CUSTOMER_USERNAME = "customer-user";

    private ReviewResponseDto sampleReview(Long id, String vendorPublicId, Integer rating) {
        return ReviewResponseDto.builder()
                .reviewId(id)
                .vendorPublicId(vendorPublicId)
                .restaurantName("Mama's Kitchen")
                .rating(rating)
                .isVisible(true)
                .isVendorReview(true)
                .build();
    }

    private ReviewRequestDto validRequest() {
        return ReviewRequestDto.builder()
                .vendorPublicId("vendor-1")
                .rating(5)
                .comment("Great food!")
                .orderPublicId("order-1")
                .build();
    }

    // ========== PUBLIC ENDPOINTS ==========

    @Test
    void getVendorReviews_returns200() throws Exception {
        when(reviewService.getVendorReviews("vendor-1"))
                .thenReturn(List.of(sampleReview(1L, "vendor-1", 5)));

        mockMvc.perform(get("/vendors/{vendorPublicId}/reviews", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].rating").value(5));
    }

    @Test
    void getProductReviews_returns200() throws Exception {
        when(reviewService.getProductReviews("product-1"))
                .thenReturn(List.of(sampleReview(1L, "vendor-1", 4)));

        mockMvc.perform(get("/products/{productPublicId}/reviews", "product-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rating").value(4));
    }

    @Test
    void getVendorAverageRating_returns200() throws Exception {
        when(reviewService.getVendorAverageRating("vendor-1")).thenReturn(4.5);

        mockMvc.perform(get("/vendors/{vendorPublicId}/rating", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(4.5));
    }

    @Test
    void getProductAverageRating_returns200() throws Exception {
        when(reviewService.getProductAverageRating("product-1")).thenReturn(3.5);

        mockMvc.perform(get("/products/{productPublicId}/rating", "product-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3.5));
    }

    @Test
    void getReviewsByMinimumRating_returns200() throws Exception {
        when(reviewService.getReviewsByMinimumRating("vendor-1", 4))
                .thenReturn(List.of(sampleReview(1L, "vendor-1", 5)));

        mockMvc.perform(get("/vendors/{vendorPublicId}/reviews/filter", "vendor-1")
                        .param("minRating", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rating").value(5));
    }

    @Test
    void getReviewsByMinimumRating_missingParam_returns400() throws Exception {
        mockMvc.perform(get("/vendors/{vendorPublicId}/reviews/filter", "vendor-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: minRating"));
    }

    @Test
    void markReviewAsHelpful_returns200() throws Exception {
        when(reviewService.markReviewAsHelpful(1L)).thenReturn(sampleReview(1L, "vendor-1", 5));

        mockMvc.perform(patch("/reviews/{reviewId}/helpful", 1L)
                        .with(authenticatedAs(CUSTOMER_USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewId").value(1));
    }

    // ========== CUSTOMER ENDPOINTS ==========

    @Test
    void createReview_valid_returns201() throws Exception {
        when(reviewService.createReview(eq(CUSTOMER_USERNAME), any(ReviewRequestDto.class)))
                .thenReturn(sampleReview(1L, "vendor-1", 5));

        mockMvc.perform(post("/customer/reviews")
                        .with(authenticatedAs(CUSTOMER_USERNAME, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(5));
    }

    @Test
    void createReview_missingRating_returns400WithValidationErrors() throws Exception {
        ReviewRequestDto request = validRequest();
        request.setRating(null);

        mockMvc.perform(post("/customer/reviews")
                        .with(authenticatedAs(CUSTOMER_USERNAME, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("rating"));

        verify(reviewService, never()).createReview(any(), any());
    }

    @Test
    void updateReview_valid_returns200() throws Exception {
        when(reviewService.updateReview(eq(CUSTOMER_USERNAME), eq(1L), any(ReviewRequestDto.class)))
                .thenReturn(sampleReview(1L, "vendor-1", 4));

        mockMvc.perform(put("/customer/reviews/{reviewId}", 1L)
                        .with(authenticatedAs(CUSTOMER_USERNAME, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(4));
    }

    @Test
    void deleteReview_returns200() throws Exception {
        doNothing().when(reviewService).deleteReview(CUSTOMER_USERNAME, 1L);

        mockMvc.perform(delete("/customer/reviews/{reviewId}", 1L)
                        .with(authenticatedAs(CUSTOMER_USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getMyReviews_returns200() throws Exception {
        when(reviewService.getMyReviews(CUSTOMER_USERNAME))
                .thenReturn(List.of(sampleReview(1L, "vendor-1", 5)));

        mockMvc.perform(get("/customer/reviews").with(authenticatedAs(CUSTOMER_USERNAME, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reviewId").value(1));
    }

    @Test
    void getReviewEligibility_returns200() throws Exception {
        ReviewService.ReviewEligibilityDto eligibility = ReviewService.ReviewEligibilityDto.builder()
                .hasOrdered(true)
                .alreadyReviewed(false)
                .canReview(true)
                .eligibleOrders(List.of())
                .build();
        when(reviewService.getEligibleOrders(CUSTOMER_USERNAME, "vendor-1")).thenReturn(eligibility);

        mockMvc.perform(get("/customer/reviews/eligible")
                        .with(authenticatedAs(CUSTOMER_USERNAME, "CUSTOMER"))
                        .param("vendorPublicId", "vendor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canReview").value(true));
    }

    // ========== VENDOR ENDPOINTS (use @AuthenticationPrincipal) ==========

    @Test
    void getMyVendorReviews_returns200() throws Exception {
        User vendorUser = User.builder().publicUserId("vendor-1").build();
        when(reviewService.getMyVendorReviews("vendor-1"))
                .thenReturn(List.of(sampleReview(1L, "vendor-1", 5)));

        mockMvc.perform(get("/vendor/reviews")
                        .with(authenticatedAsPrincipal(vendorUser, "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].vendorPublicId").value("vendor-1"));
    }

    @Test
    void getVendorStats_returns200() throws Exception {
        User vendorUser = User.builder().publicUserId("vendor-1").build();
        ReviewService.VendorReviewStats stats = ReviewService.VendorReviewStats.builder()
                .totalReviews(10L)
                .visibleReviews(9L)
                .hiddenReviews(1L)
                .averageRating(4.2)
                .build();
        when(reviewService.getVendorStats("vendor-1")).thenReturn(stats);

        mockMvc.perform(get("/vendor/reviews/stats")
                        .with(authenticatedAsPrincipal(vendorUser, "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReviews").value(10));
    }

    // ========== ADMIN ENDPOINTS (security not exercised in this slice) ==========

    @Test
    void getAllReviews_returns200() throws Exception {
        when(reviewService.getAllReviews())
                .thenReturn(List.of(sampleReview(1L, "vendor-1", 5), sampleReview(2L, "vendor-2", 3)));

        mockMvc.perform(get("/admin/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getHiddenReviews_returns200() throws Exception {
        when(reviewService.getHiddenReviews()).thenReturn(List.of(sampleReview(1L, "vendor-1", 1)));

        mockMvc.perform(get("/admin/reviews/hidden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void hideReview_returns200() throws Exception {
        when(reviewService.hideReview(1L)).thenReturn(sampleReview(1L, "vendor-1", 1));

        mockMvc.perform(patch("/admin/reviews/{reviewId}/hide", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewId").value(1));
    }

    @Test
    void showReview_returns200() throws Exception {
        when(reviewService.showReview(1L)).thenReturn(sampleReview(1L, "vendor-1", 5));

        mockMvc.perform(patch("/admin/reviews/{reviewId}/show", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewId").value(1));
    }

    @Test
    void adminDeleteReview_returns200() throws Exception {
        doNothing().when(reviewService).adminDeleteReview(1L);

        mockMvc.perform(delete("/admin/reviews/{reviewId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getAdminStats_returns200() throws Exception {
        ReviewService.AdminReviewStats stats = ReviewService.AdminReviewStats.builder()
                .totalReviews(100L)
                .visibleReviews(95L)
                .hiddenReviews(5L)
                .build();
        when(reviewService.getAdminStats()).thenReturn(stats);

        mockMvc.perform(get("/admin/reviews/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReviews").value(100));
    }
}
