package com.afrochow.promotion.service;

import com.afrochow.common.enums.PromotionType;
import com.afrochow.order.model.Order;
import com.afrochow.promotion.model.Promotion;
import com.afrochow.promotion.repository.PromotionRepository;
import com.afrochow.promotion.repository.PromotionUsageRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused on calculateDiscount() and recordUsage() — the two methods OrderService
 * actually calls at checkout time (see OrderServiceTest's promo-code coverage).
 * The admin/vendor CRUD and preview endpoints share the same validation and
 * discount-computation helpers, so this gives solid coverage of the core logic
 * without duplicating every admin-side test.
 */
@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionUsageRepository promotionUsageRepository;
    @Mock private UserRepository userRepository;
    @Mock private VendorProfileRepository vendorProfileRepository;

    @InjectMocks
    private PromotionService promotionService;

    private User customerUser;
    private VendorProfile vendor;

    @BeforeEach
    void setUp() {
        customerUser = User.builder().userId(1L).publicUserId("CUS123").build();

        User vendorUser = User.builder().userId(2L).publicUserId("VEN123").build();
        vendor = VendorProfile.builder().id(5L).user(vendorUser).restaurantName("Jollof House").build();
    }

    private Promotion.PromotionBuilder activePromo() {
        LocalDateTime now = LocalDateTime.now();
        return Promotion.builder()
                .promotionId(1L)
                .publicPromotionId("PROMO-ABCD1234")
                .code("SAVE10")
                .title("Save 10%")
                .isActive(true)
                .startDate(now.minusDays(1))
                .endDate(now.plusDays(1));
    }

    // ========== calculateDiscount ==========

    @Test
    void calculateDiscount_percentage_appliesPercentOfSubtotal() {
        Promotion promo = activePromo().type(PromotionType.PERCENTAGE).value(new BigDecimal("10")).build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));

        BigDecimal discount = promotionService.calculateDiscount(
                "save10", new BigDecimal("50.00"), "CUS123", "VEN123", BigDecimal.ZERO);

        assertThat(discount).isEqualByComparingTo("5.00");
    }

    @Test
    void calculateDiscount_percentage_cappedByMaxDiscountAmount() {
        Promotion promo = activePromo().type(PromotionType.PERCENTAGE).value(new BigDecimal("50"))
                .maxDiscountAmount(new BigDecimal("10.00")).build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));

        // 50% of $100 = $50, but capped at $10
        BigDecimal discount = promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("100.00"), "CUS123", "VEN123", BigDecimal.ZERO);

        assertThat(discount).isEqualByComparingTo("10.00");
    }

    @Test
    void calculateDiscount_fixedAmount_appliesFlatDiscount() {
        Promotion promo = activePromo().type(PromotionType.FIXED_AMOUNT).value(new BigDecimal("7.50")).build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));

        BigDecimal discount = promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("50.00"), "CUS123", "VEN123", BigDecimal.ZERO);

        assertThat(discount).isEqualByComparingTo("7.50");
    }

    @Test
    void calculateDiscount_fixedAmount_neverExceedsSubtotal() {
        Promotion promo = activePromo().type(PromotionType.FIXED_AMOUNT).value(new BigDecimal("50.00")).build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));

        // $50 flat discount on a $30 subtotal should clamp to $30, not go negative.
        BigDecimal discount = promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("30.00"), "CUS123", "VEN123", BigDecimal.ZERO);

        assertThat(discount).isEqualByComparingTo("30.00");
    }

    @Test
    void calculateDiscount_freeDelivery_discountsExactlyTheDeliveryFee() {
        Promotion promo = activePromo().type(PromotionType.FREE_DELIVERY).value(BigDecimal.ZERO).build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));

        BigDecimal discount = promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("50.00"), "CUS123", "VEN123", new BigDecimal("6.00"));

        assertThat(discount).isEqualByComparingTo("6.00");
    }

    @Test
    void calculateDiscount_freeDeliveryOnPickup_throwsIllegalState() {
        Promotion promo = activePromo().type(PromotionType.FREE_DELIVERY).value(BigDecimal.ZERO).build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("50.00"), "CUS123", "VEN123", BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be applied to pickup orders");
    }

    @Test
    void calculateDiscount_codeNotFound_throwsIllegalArgument() {
        when(promotionRepository.findByCodeWithLock("BADCODE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.calculateDiscount(
                "badcode", new BigDecimal("50.00"), "CUS123", "VEN123", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid promo code");
    }

    @Test
    void calculateDiscount_expiredPromotion_throwsIllegalState() {
        LocalDateTime now = LocalDateTime.now();
        Promotion promo = Promotion.builder().code("SAVE10").type(PromotionType.PERCENTAGE)
                .value(new BigDecimal("10")).isActive(true)
                .startDate(now.minusDays(10)).endDate(now.minusDays(1)) // ended yesterday
                .build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("50.00"), "CUS123", "VEN123", BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired or inactive");
    }

    @Test
    void calculateDiscount_wrongVendor_throwsIllegalState() {
        Promotion promo = activePromo().type(PromotionType.PERCENTAGE).value(new BigDecimal("10"))
                .vendor(vendor).build(); // vendor.user.publicUserId = VEN123
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("50.00"), "CUS123", "SOME_OTHER_VENDOR", BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid for this vendor");
    }

    @Test
    void calculateDiscount_belowMinimumOrderAmount_throwsIllegalState() {
        Promotion promo = activePromo().type(PromotionType.PERCENTAGE).value(new BigDecimal("10"))
                .minimumOrderAmount(new BigDecimal("25.00")).build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("20.00"), "CUS123", "VEN123", BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Minimum order amount");
    }

    @Test
    void calculateDiscount_globalUsageLimitReached_throwsIllegalState() {
        Promotion promo = activePromo().type(PromotionType.PERCENTAGE).value(new BigDecimal("10"))
                .usageLimit(100).build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));
        when(promotionUsageRepository.countByPromotion(promo)).thenReturn(100L);

        assertThatThrownBy(() -> promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("50.00"), "CUS123", "VEN123", BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("usage limit");
    }

    @Test
    void calculateDiscount_perUserLimitReached_throwsIllegalState() {
        Promotion promo = activePromo().type(PromotionType.PERCENTAGE).value(new BigDecimal("10"))
                .perUserLimit(1).build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));
        when(userRepository.findByPublicUserId("CUS123")).thenReturn(Optional.of(customerUser));
        when(promotionUsageRepository.countByPromotionAndUser(promo, customerUser)).thenReturn(1L);

        assertThatThrownBy(() -> promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("50.00"), "CUS123", "VEN123", BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maximum number of times");
    }

    @Test
    void calculateDiscount_perUserLimitSetButUserMissing_throwsEntityNotFound() {
        Promotion promo = activePromo().type(PromotionType.PERCENTAGE).value(new BigDecimal("10"))
                .perUserLimit(1).build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));
        when(userRepository.findByPublicUserId("CUS123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.calculateDiscount(
                "SAVE10", new BigDecimal("50.00"), "CUS123", "VEN123", BigDecimal.ZERO))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== recordUsage ==========

    @Test
    void recordUsage_success_savesUsageRecord() {
        Promotion promo = activePromo().type(PromotionType.PERCENTAGE).value(new BigDecimal("10")).build();
        Order order = Order.builder().publicOrderId("AFC-TEST0001").build();
        when(promotionRepository.findByCodeWithLock("SAVE10")).thenReturn(Optional.of(promo));

        promotionService.recordUsage("save10", customerUser, order, new BigDecimal("5.00"));

        verify(promotionUsageRepository).save(argThat(usage ->
                usage.getPromotion() == promo
                        && usage.getUser() == customerUser
                        && usage.getOrder() == order
                        && usage.getDiscountApplied().compareTo(new BigDecimal("5.00")) == 0));
    }

    @Test
    void recordUsage_promotionNotFound_throwsEntityNotFound() {
        when(promotionRepository.findByCodeWithLock("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.recordUsage(
                "missing", customerUser, Order.builder().build(), new BigDecimal("5.00")))
                .isInstanceOf(EntityNotFoundException.class);
        verify(promotionUsageRepository, never()).save(any());
    }

    // Small local helper — avoids pulling in Mockito's ArgumentMatcher import ceremony
    // for the one predicate-based verification above.
    private static com.afrochow.promotion.model.PromotionUsage argThat(
            java.util.function.Predicate<com.afrochow.promotion.model.PromotionUsage> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
