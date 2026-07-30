package com.afrochow.review.service;

import com.afrochow.common.enums.OrderStatus;
import com.afrochow.common.enums.Role;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.customer.repository.CustomerProfileRepository;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.review.dto.ReviewRequestDto;
import com.afrochow.review.dto.ReviewResponseDto;
import com.afrochow.review.model.Review;
import com.afrochow.review.repository.ReviewRepository;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private UserRepository userRepository;
    @Mock private VendorProfileRepository vendorProfileRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerProfileRepository customerProfileRepository;
    @Mock private OutboxEventService outboxEventService;

    @InjectMocks private ReviewService reviewService;

    private User customerUser;
    private User vendorUser;
    private VendorProfile vendor;
    private CustomerProfile customerProfile;
    private Order deliveredOrder;
    private Product product;
    private Review review;

    @BeforeEach
    void setUp() {
        customerUser = User.builder().userId(1L).publicUserId("CUS1").username("adecustomer")
                .email("customer@example.com").firstName("Ade").lastName("Customer")
                .role(Role.CUSTOMER).build();
        vendorUser = User.builder().userId(2L).publicUserId("VEN1").username("jollofhouse")
                .role(Role.VENDOR).build();
        vendor = VendorProfile.builder().id(5L).user(vendorUser).restaurantName("Jollof House").build();
        customerProfile = CustomerProfile.builder().user(customerUser).build();
        deliveredOrder = Order.builder().publicOrderId("AFC-0001").status(OrderStatus.DELIVERED)
                .customer(customerProfile).vendor(vendor).build();
        product = Product.builder().productId(1L).publicProductId("PROD-1").name("Jollof Rice")
                .vendor(vendor).build();
        review = Review.builder().reviewId(100L).user(customerUser).vendor(vendor)
                .rating(5).comment("Great!").isVisible(true).helpfulCount(0)
                .createdAt(LocalDateTime.now()).build();
    }

    // ========== createReview ==========

    @Test
    void createReview_validDeliveredOrder_savesAndFiresOutboxEvent() {
        ReviewRequestDto request = ReviewRequestDto.builder()
                .vendorPublicId("VEN1").orderPublicId("AFC-0001").rating(5).comment("Amazing").build();
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customerUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(deliveredOrder));
        when(reviewRepository.existsByUserAndVendorAndProductIsNull(customerUser, vendor)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setReviewId(200L);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReviewResponseDto result = reviewService.createReview("CUS1", request);

        assertThat(result.getRating()).isEqualTo(5);
        verify(outboxEventService).vendorReviewed(eq("VEN1"), eq("Ade Customer"), eq(5), eq("restaurant"));
    }

    @Test
    void createReview_withProduct_checksProductDuplicateNotVendorDuplicate() {
        ReviewRequestDto request = ReviewRequestDto.builder()
                .vendorPublicId("VEN1").productPublicId("PROD-1")
                .orderPublicId("AFC-0001").rating(4).build();
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customerUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(deliveredOrder));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserAndProduct(customerUser, product)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReviewResponseDto result = reviewService.createReview("CUS1", request);

        assertThat(result.getProductPublicId()).isEqualTo("PROD-1");
        verify(reviewRepository, never()).existsByUserAndVendorAndProductIsNull(any(), any());
        verify(outboxEventService).vendorReviewed(eq("VEN1"), anyString(), eq(4), eq("product"));
    }

    @Test
    void createReview_orderNotBelongingToUser_throwsIllegalState() {
        User otherUser = User.builder().userId(3L).publicUserId("CUS2")
                .email("other@example.com").username("other").role(Role.CUSTOMER).build();
        ReviewRequestDto request = ReviewRequestDto.builder()
                .vendorPublicId("VEN1").orderPublicId("AFC-0001").rating(5).build();
        when(userRepository.findByPublicUserId("CUS2")).thenReturn(Optional.of(otherUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(deliveredOrder));

        assertThatThrownBy(() -> reviewService.createReview("CUS2", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void createReview_orderNotDelivered_throwsIllegalState() {
        deliveredOrder.setStatus(OrderStatus.CONFIRMED);
        ReviewRequestDto request = ReviewRequestDto.builder()
                .vendorPublicId("VEN1").orderPublicId("AFC-0001").rating(5).build();
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customerUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(deliveredOrder));

        assertThatThrownBy(() -> reviewService.createReview("CUS1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivered");
    }

    @Test
    void createReview_orderFromDifferentVendor_throwsIllegalState() {
        User otherVendorUser = User.builder().userId(4L).publicUserId("VEN2").role(Role.VENDOR).build();
        VendorProfile otherVendor = VendorProfile.builder().id(6L).user(otherVendorUser)
                .restaurantName("Other Spot").build();
        deliveredOrder.setVendor(otherVendor);
        ReviewRequestDto request = ReviewRequestDto.builder()
                .vendorPublicId("VEN1").orderPublicId("AFC-0001").rating(5).build();
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customerUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(deliveredOrder));

        assertThatThrownBy(() -> reviewService.createReview("CUS1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not placed with the selected vendor");
    }

    @Test
    void createReview_duplicateVendorReview_throwsIllegalState() {
        ReviewRequestDto request = ReviewRequestDto.builder()
                .vendorPublicId("VEN1").orderPublicId("AFC-0001").rating(5).build();
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customerUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(deliveredOrder));
        when(reviewRepository.existsByUserAndVendorAndProductIsNull(customerUser, vendor)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview("CUS1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already reviewed this store");
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_duplicateProductReview_throwsIllegalState() {
        ReviewRequestDto request = ReviewRequestDto.builder()
                .vendorPublicId("VEN1").productPublicId("PROD-1")
                .orderPublicId("AFC-0001").rating(5).build();
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customerUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(deliveredOrder));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserAndProduct(customerUser, product)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview("CUS1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already reviewed this product");
    }

    @Test
    void createReview_orderNotFound_throwsEntityNotFound() {
        ReviewRequestDto request = ReviewRequestDto.builder()
                .vendorPublicId("VEN1").orderPublicId("missing").rating(5).build();
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customerUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(orderRepository.findByPublicOrderId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview("CUS1", request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== updateReview ==========

    @Test
    void updateReview_ownedAndWithinWindow_updatesContent() {
        ReviewRequestDto request = ReviewRequestDto.builder().rating(3).comment("Updated").build();
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponseDto result = reviewService.updateReview("CUS1", 100L, request);

        assertThat(result.getRating()).isEqualTo(3);
        assertThat(result.getComment()).isEqualTo("Updated");
    }

    @Test
    void updateReview_notOwner_throwsIllegalState() {
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.updateReview("someoneelse@example.com", 100L,
                ReviewRequestDto.builder().rating(1).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("your own reviews");
    }

    @Test
    void updateReview_outsideEditWindow_throwsIllegalState() {
        review.setCreatedAt(LocalDateTime.now().minusHours(25));
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.updateReview("CUS1", 100L,
                ReviewRequestDto.builder().rating(1).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("within 24 hours");
    }

    @Test
    void updateReview_notFound_throwsEntityNotFound() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.updateReview("CUS1", 999L,
                ReviewRequestDto.builder().rating(1).build()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== deleteReview ==========

    @Test
    void deleteReview_ownedAndWithinWindow_deletes() {
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        reviewService.deleteReview("CUS1", 100L);

        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_notOwner_throwsIllegalState() {
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.deleteReview("nobody@example.com", 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("your own reviews");
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void deleteReview_outsideEditWindow_throwsIllegalState() {
        review.setCreatedAt(LocalDateTime.now().minusHours(25));
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.deleteReview("CUS1", 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("within 24 hours");
    }

    // ========== markReviewAsHelpful ==========

    @Test
    void markReviewAsHelpful_incrementsCount() {
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponseDto result = reviewService.markReviewAsHelpful(100L);

        assertThat(result.getHelpfulCount()).isEqualTo(1);
    }

    @Test
    void markReviewAsHelpful_notFound_throwsEntityNotFound() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.markReviewAsHelpful(999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== admin moderation ==========

    @Test
    void hideReview_setsInvisible() {
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponseDto result = reviewService.hideReview(100L);

        assertThat(result.getIsVisible()).isFalse();
    }

    @Test
    void showReview_setsVisible() {
        review.setIsVisible(false);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponseDto result = reviewService.showReview(100L);

        assertThat(result.getIsVisible()).isTrue();
    }

    @Test
    void adminDeleteReview_deletesRegardlessOfWindow() {
        review.setCreatedAt(LocalDateTime.now().minusDays(30));
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        reviewService.adminDeleteReview(100L);

        verify(reviewRepository).delete(review);
    }

    @Test
    void getAdminStats_computesHiddenAsDifference() {
        when(reviewRepository.count()).thenReturn(10L);
        when(reviewRepository.findByIsVisible(true)).thenReturn(List.of(review, review, review, review, review, review, review, review));

        ReviewService.AdminReviewStats stats = reviewService.getAdminStats();

        assertThat(stats.getTotalReviews()).isEqualTo(10L);
        assertThat(stats.getVisibleReviews()).isEqualTo(8L);
        assertThat(stats.getHiddenReviews()).isEqualTo(2L);
    }

    // ========== eligibility ==========

    @Test
    void getEligibleOrders_hasDeliveredOrderAndNoReview_canReviewTrue() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customerUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(customerProfileRepository.findByUser(customerUser)).thenReturn(Optional.of(customerProfile));
        when(orderRepository.findByCustomerAndVendorAndStatus(customerProfile, vendor, OrderStatus.DELIVERED))
                .thenReturn(List.of(deliveredOrder));
        when(reviewRepository.existsByUserAndVendorAndProductIsNull(customerUser, vendor)).thenReturn(false);

        ReviewService.ReviewEligibilityDto result = reviewService.getEligibleOrders("CUS1", "VEN1");

        assertThat(result.isHasOrdered()).isTrue();
        assertThat(result.isAlreadyReviewed()).isFalse();
        assertThat(result.isCanReview()).isTrue();
        assertThat(result.getEligibleOrders()).hasSize(1);
    }

    @Test
    void getEligibleOrders_alreadyReviewed_canReviewFalse() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customerUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(customerProfileRepository.findByUser(customerUser)).thenReturn(Optional.of(customerProfile));
        when(orderRepository.findByCustomerAndVendorAndStatus(customerProfile, vendor, OrderStatus.DELIVERED))
                .thenReturn(List.of(deliveredOrder));
        when(reviewRepository.existsByUserAndVendorAndProductIsNull(customerUser, vendor)).thenReturn(true);

        ReviewService.ReviewEligibilityDto result = reviewService.getEligibleOrders("CUS1", "VEN1");

        assertThat(result.isCanReview()).isFalse();
    }

    @Test
    void getEligibleOrders_noDeliveredOrders_hasOrderedFalse() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customerUser));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(customerProfileRepository.findByUser(customerUser)).thenReturn(Optional.of(customerProfile));
        when(orderRepository.findByCustomerAndVendorAndStatus(customerProfile, vendor, OrderStatus.DELIVERED))
                .thenReturn(List.of());
        when(reviewRepository.existsByUserAndVendorAndProductIsNull(customerUser, vendor)).thenReturn(false);

        ReviewService.ReviewEligibilityDto result = reviewService.getEligibleOrders("CUS1", "VEN1");

        assertThat(result.isHasOrdered()).isFalse();
        assertThat(result.isCanReview()).isFalse();
        assertThat(result.getEligibleOrders()).isEmpty();
    }

    // ========== public read methods ==========

    @Test
    void getVendorReviews_returnsVisibleOnly() {
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(reviewRepository.findByVendorAndIsVisible(vendor, true)).thenReturn(List.of(review));

        List<ReviewResponseDto> result = reviewService.getVendorReviews("VEN1");

        assertThat(result).hasSize(1);
    }

    @Test
    void getVendorAverageRating_roundsToOneDecimal() {
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(reviewRepository.calculateVendorAverageRating(vendor)).thenReturn(4.567);

        Double result = reviewService.getVendorAverageRating("VEN1");

        assertThat(result).isEqualTo(4.6);
    }

    @Test
    void getVendorAverageRating_noReviews_returnsZero() {
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(reviewRepository.calculateVendorAverageRating(vendor)).thenReturn(null);

        Double result = reviewService.getVendorAverageRating("VEN1");

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void getReviewsByMinimumRating_filtersHiddenReviewsInMemory() {
        Review hiddenHighRated = Review.builder().reviewId(101L).user(customerUser).vendor(vendor)
                .rating(5).isVisible(false).createdAt(LocalDateTime.now()).build();
        when(vendorProfileRepository.findByUser_PublicUserId("VEN1")).thenReturn(Optional.of(vendor));
        when(reviewRepository.findByVendorAndRatingGreaterThanEqual(vendor, 4))
                .thenReturn(List.of(review, hiddenHighRated));

        List<ReviewResponseDto> result = reviewService.getReviewsByMinimumRating("VEN1", 4);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReviewId()).isEqualTo(100L);
    }
}
