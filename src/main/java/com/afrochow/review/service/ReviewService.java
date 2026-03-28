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

import com.afrochow.customer.repository.CustomerProfileRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final CustomerProfileRepository customerProfileRepository;
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
     * Create a new review (customer only).
     *
     * Rules enforced here:
     *  1. The customer must provide a valid orderPublicId.
     *  2. That order must belong to the authenticated customer.
     *  3. That order must have status DELIVERED.
     *  4. That order must be from the same vendor being reviewed.
     *  5. The customer may not leave a second vendor-level review for the same vendor,
     *     or a second product-level review for the same product.
     */
    @Transactional
    public ReviewResponseDto createReview(String userPublicId, ReviewRequestDto request) {
        User user = resolveUser(userPublicId);

        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(request.getVendorPublicId())
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        // ── 1. Order is mandatory ──────────────────────────────────────────────
        Order order = orderRepository.findByPublicOrderId(request.getOrderPublicId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        // ── 2. Order must belong to the authenticated customer ─────────────────
        User orderOwner = order.getCustomer().getUser();
        if (!isOwner(orderOwner, userPublicId)) {
            throw new IllegalStateException("Order does not belong to this user");
        }

        // ── 3. Order must be delivered ─────────────────────────────────────────
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("You can only review a completed (delivered) order");
        }

        // ── 4. Order must be from the vendor being reviewed ────────────────────
        if (!order.getVendor().getUser().getPublicUserId().equals(vendor.getUser().getPublicUserId())) {
            throw new IllegalStateException("This order was not placed with the selected vendor");
        }

        // ── 5. Duplicate review guard ──────────────────────────────────────────
        Product product = null;
        if (request.getProductPublicId() != null) {
            product = productRepository.findByPublicProductId(request.getProductPublicId())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found"));
            if (reviewRepository.existsByUserAndProduct(user, product)) {
                throw new IllegalStateException("You have already reviewed this product");
            }
        } else {
            if (reviewRepository.existsByUserAndVendorAndProductIsNull(user, vendor)) {
                throw new IllegalStateException("You have already reviewed this store");
            }
        }

        // ── Build and persist ──────────────────────────────────────────────────
        Review review = toEntity(request);
        review.setUser(user);
        review.setVendor(vendor);
        review.setOrder(order);
        if (product != null) review.setProduct(product);

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
        if (!isOwner(review.getUser(), userPublicId)) {
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
        if (!isOwner(review.getUser(), userPublicId)) {
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
        User user = resolveUser(userPublicId);

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

    // ========== REVIEW ELIGIBILITY ==========

    /**
     * Returns review eligibility information for an authenticated customer and a specific vendor.
     *
     * <ul>
     *   <li>{@code hasOrdered}      – the customer has at least one DELIVERED order from this vendor</li>
     *   <li>{@code alreadyReviewed} – the customer has already submitted a store-level review</li>
     *   <li>{@code canReview}       – hasOrdered && !alreadyReviewed</li>
     *   <li>{@code eligibleOrders}  – the DELIVERED orders available to attach to a new review</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public ReviewEligibilityDto getEligibleOrders(String userPublicId, String vendorPublicId) {
        User user = resolveUser(userPublicId);

        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        com.afrochow.customer.model.CustomerProfile customerProfile =
                customerProfileRepository.findByUser(user)
                        .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        List<Order> deliveredOrders = orderRepository
                .findByCustomerAndVendorAndStatus(customerProfile, vendor, OrderStatus.DELIVERED);

        boolean hasOrdered = !deliveredOrders.isEmpty();
        boolean alreadyReviewed = reviewRepository.existsByUserAndVendorAndProductIsNull(user, vendor);

        List<EligibleOrderDto> orderDtos = deliveredOrders.stream()
                .map(o -> EligibleOrderDto.builder()
                        .publicOrderId(o.getPublicOrderId())
                        .orderTime(o.getOrderTime())
                        .totalAmount(o.getTotalAmount())
                        .build())
                .collect(Collectors.toList());

        return ReviewEligibilityDto.builder()
                .hasOrdered(hasOrdered)
                .alreadyReviewed(alreadyReviewed)
                .canReview(hasOrdered && !alreadyReviewed)
                .eligibleOrders(orderDtos)
                .build();
    }

    // ========== INNER CLASSES FOR STATISTICS ==========

    @lombok.Data
    @lombok.Builder
    public static class EligibleOrderDto {
        private String publicOrderId;
        private LocalDateTime orderTime;
        private BigDecimal totalAmount;
    }

    @lombok.Data
    @lombok.Builder
    public static class ReviewEligibilityDto {
        private boolean hasOrdered;
        private boolean alreadyReviewed;
        private boolean canReview;
        private List<EligibleOrderDto> eligibleOrders;
    }

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

    /** Resolve a user by publicUserId, email, or username — whichever matches. */
    private User resolveUser(String identifier) {
        return userRepository.findByPublicUserId(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + identifier));
    }

    /** Check ownership against all three possible identifier forms. */
    private boolean isOwner(User user, String identifier) {
        return user.getPublicUserId().equals(identifier)
                || user.getEmail().equals(identifier)
                || user.getUsername().equals(identifier);
    }
}
