package com.afrochow.review.service;

import com.afrochow.review.dto.ReviewRequestDto;
import com.afrochow.review.dto.ReviewResponseDto;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.notification.service.NotificationService;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.review.model.Review;
import com.afrochow.review.repository.ReviewRepository;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    // ========== PUBLIC METHODS (No Authentication Required) ==========

    /**
     * Get all visible reviews for a vendor
     */
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getVendorReviews(String vendorPublicId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        List<Review> reviews = reviewRepository.findByVendorAndIsVisible(vendor, true);
        return reviews.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get all visible reviews for a product
     */
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getProductReviews(String productPublicId) {
        Product product = productRepository.findByPublicProductId(productPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        List<Review> reviews = reviewRepository.findByProductAndIsVisible(product, true);
        return reviews.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get vendor's average rating
     */
    @Transactional(readOnly = true)
    public Double getVendorAverageRating(String vendorPublicId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        Double avgRating = reviewRepository.calculateVendorAverageRating(vendor);
        return avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0;
    }

    /**
     * Get product's average rating
     */
    @Transactional(readOnly = true)
    public Double getProductAverageRating(String productPublicId) {
        Product product = productRepository.findByPublicProductId(productPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        Double avgRating = reviewRepository.calculateProductAverageRating(product);
        return avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0;
    }

    /**
     * Get reviews by minimum rating (e.g., all 4+ star reviews)
     */
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getReviewsByMinimumRating(String vendorPublicId, Integer minRating) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        List<Review> reviews = reviewRepository.findByVendorAndRatingGreaterThanEqual(vendor, minRating);
        return reviews.stream()
                .filter(Review::getIsVisible)
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    // ========== CUSTOMER METHODS (Authenticated Customers) ==========

    /**
     * Create a new review (customer only)
     */
    @Transactional
    public ReviewResponseDto createReview(String userPublicId, ReviewRequestDto request) {
        User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(request.getVendorPublicId())
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        // Create review entity
        Review review = toEntity(request);
        review.setUser(user);
        review.setVendor(vendor);

        // Set product if provided
        if (request.getProductPublicId() != null) {
            Product product = productRepository.findByPublicProductId(request.getProductPublicId())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found"));
            review.setProduct(product);
        }

        // Set order if provided
        if (request.getOrderPublicId() != null) {
            Order order = orderRepository.findByPublicOrderId(request.getOrderPublicId())
                    .orElseThrow(() -> new EntityNotFoundException("Order not found"));

            // Validate that the order belongs to this user
            if (!order.getCustomer().getUser().getPublicUserId().equals(userPublicId)) {
                throw new IllegalStateException("Order does not belong to this user");
            }

            // Validate that the order is completed
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw new IllegalStateException("Can only review completed orders");
            }

            review.setOrder(order);
        }

        Review savedReview = reviewRepository.save(review);

        // Notify vendor of new review (in-app notification only)
        String reviewerName = user.getFirstName() + " " + user.getLastName();
        String reviewType = savedReview.getProduct() != null ? "product" : "restaurant";
        notificationService.notifyVendorNewReview(
            vendor.getUser().getPublicUserId(),
            reviewerName,
            savedReview.getRating(),
            reviewType
        );

        return toResponseDto(savedReview);
    }

    /**
     * Update an existing review (customer only, within 24 hours)
     */
    @Transactional
    public ReviewResponseDto updateReview(String userPublicId, Long reviewId, ReviewRequestDto request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        // Validate ownership
        if (!review.getUser().getPublicUserId().equals(userPublicId)) {
            throw new IllegalStateException("You can only update your own reviews");
        }

        // Validate edit window (24 hours)
        if (!review.canBeEdited()) {
            throw new IllegalStateException("Reviews can only be edited within 24 hours of creation");
        }

        // Update review
        review.updateReview(request.getRating(), request.getComment());

        Review updatedReview = reviewRepository.save(review);
        return toResponseDto(updatedReview);
    }

    /**
     * Delete a review (customer only, within 24 hours)
     */
    @Transactional
    public void deleteReview(String userPublicId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        // Validate ownership
        if (!review.getUser().getPublicUserId().equals(userPublicId)) {
            throw new IllegalStateException("You can only delete your own reviews");
        }

        // Validate edit window (24 hours)
        if (!review.canBeEdited()) {
            throw new IllegalStateException("Reviews can only be deleted within 24 hours of creation");
        }

        reviewRepository.delete(review);
    }

    /**
     * Get all reviews written by a customer
     */
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getMyReviews(String userPublicId) {
        User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<Review> reviews = reviewRepository.findByUserOrderByCreatedAtDesc(user);
        return reviews.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Mark a review as helpful (anyone can do this)
     */
    @Transactional
    public ReviewResponseDto markReviewAsHelpful(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        review.markAsHelpful();
        Review updatedReview = reviewRepository.save(review);
        return toResponseDto(updatedReview);
    }

    // ========== VENDOR METHODS (Authenticated Vendors) ==========

    /**
     * Get all reviews for vendor's restaurant (including hidden ones)
     */
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getMyVendorReviews(String vendorPublicId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        List<Review> reviews = reviewRepository.findByVendorOrderByCreatedAtDesc(vendor);
        return reviews.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get vendor statistics
     */
    @Transactional(readOnly = true)
    public VendorReviewStats getVendorStats(String vendorPublicId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        Long totalReviews = reviewRepository.countByVendor(vendor);
        Long visibleReviews = reviewRepository.countByVendorAndIsVisible(vendor, true);
        Double avgRating = reviewRepository.calculateVendorAverageRating(vendor);

        return VendorReviewStats.builder()
                .totalReviews(totalReviews)
                .visibleReviews(visibleReviews)
                .hiddenReviews(totalReviews - visibleReviews)
                .averageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0)
                .build();
    }

    // ========== ADMIN METHODS (Authenticated Admins) ==========

    /**
     * Get all reviews (admin only)
     */
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getAllReviews() {
        List<Review> reviews = reviewRepository.findAll();
        return reviews.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get only hidden reviews (admin moderation)
     */
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getHiddenReviews() {
        List<Review> reviews = reviewRepository.findByIsVisible(false);
        return reviews.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Hide a review (admin moderation)
     */
    @Transactional
    public ReviewResponseDto hideReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        review.hide();
        Review updatedReview = reviewRepository.save(review);
        return toResponseDto(updatedReview);
    }

    /**
     * Show a review (admin moderation)
     */
    @Transactional
    public ReviewResponseDto showReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        review.show();
        Review updatedReview = reviewRepository.save(review);
        return toResponseDto(updatedReview);
    }

    /**
     * Delete any review (admin only)
     */
    @Transactional
    public void adminDeleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        reviewRepository.delete(review);
    }

    /**
     * Get review statistics for admin dashboard
     */
    @Transactional(readOnly = true)
    public AdminReviewStats getAdminStats() {
        long totalReviews = reviewRepository.count();
        long visibleReviews = reviewRepository.findByIsVisible(true).size();
        long hiddenReviews = totalReviews - visibleReviews;

        return AdminReviewStats.builder()
                .totalReviews(totalReviews)
                .visibleReviews(visibleReviews)
                .hiddenReviews(hiddenReviews)
                .build();
    }

    // ========== INNER CLASSES FOR STATISTICS ==========

    @lombok.Data
    @lombok.Builder
    public static class VendorReviewStats {
        private Long totalReviews;
        private Long visibleReviews;
        private Long hiddenReviews;
        private Double averageRating;
    }

    @lombok.Data
    @lombok.Builder
    public static class AdminReviewStats {
        private Long totalReviews;
        private Long visibleReviews;
        private Long hiddenReviews;
    }

    // ========== MAPPING METHODS ==========

    private ReviewResponseDto toResponseDto(Review review) {
        return ReviewResponseDto.builder()
                .reviewId(review.getReviewId())
                .userPublicId(review.getUser() != null ? review.getUser().getPublicUserId() : null)
                .userName(review.getUser() != null ?
                        review.getUser().getFirstName() + " " + review.getUser().getLastName() : null)
                .vendorPublicId(review.getVendor() != null ? review.getVendor().getPublicVendorId() : null)
                .restaurantName(review.getVendor() != null ? review.getVendor().getRestaurantName() : null)
                .productPublicId(review.getProduct() != null ? review.getProduct().getPublicProductId() : null)
                .productName(review.getProduct() != null ? review.getProduct().getName() : null)
                .orderPublicId(review.getOrder() != null ? review.getOrder().getPublicOrderId() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .isVisible(review.getIsVisible())
                .helpfulCount(review.getHelpfulCount())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private Review toEntity(ReviewRequestDto dto) {
        return Review.builder()
                .rating(dto.getRating())
                .comment(dto.getComment())
                .build();
    }
}
