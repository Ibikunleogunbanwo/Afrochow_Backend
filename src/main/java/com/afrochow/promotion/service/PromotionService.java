package com.afrochow.promotion.service;

import com.afrochow.common.enums.PromotionType;
import com.afrochow.common.jackson.FlexibleLocalDateTimeDeserializer;
import com.afrochow.order.model.Order;
import com.afrochow.promotion.dto.PromotionPreviewRequestDto;
import com.afrochow.promotion.dto.PromotionPreviewResponseDto;
import com.afrochow.promotion.dto.PromotionRequestDto;
import com.afrochow.promotion.dto.PromotionResponseDto;
import com.afrochow.promotion.model.Promotion;
import com.afrochow.promotion.model.PromotionUsage;
import com.afrochow.promotion.repository.PromotionRepository;
import com.afrochow.promotion.repository.PromotionUsageRepository;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;

    // ========== ADMIN METHODS ==========

    @Transactional
    public PromotionResponseDto createPromotion(PromotionRequestDto request) {
        if (promotionRepository.existsByCode(request.getCode().toUpperCase().trim())) {
            throw new IllegalArgumentException("Promo code already exists: " + request.getCode());
        }
        LocalDateTime startDate = FlexibleLocalDateTimeDeserializer.parse(request.getStartDate());
        LocalDateTime endDate   = FlexibleLocalDateTimeDeserializer.parse(request.getEndDate());

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        BigDecimal resolvedValue = resolveValue(request);

        VendorProfile vendor = null;
        if (request.getVendorPublicId() != null && !request.getVendorPublicId().isBlank()) {
            vendor = vendorProfileRepository.findByPublicVendorId(request.getVendorPublicId())
                    .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));
        }

        Promotion promotion = Promotion.builder()
                .code(request.getCode().toUpperCase().trim())
                .title(request.getTitle())
                .description(request.getDescription())
                .type(request.getType())
                .value(resolvedValue)
                .maxDiscountAmount(resolveMaxDiscount(request.getMaxDiscountAmount()))
                .minimumOrderAmount(request.getMinimumOrderAmount())
                .usageLimit(request.getUsageLimit())
                .perUserLimit(request.getPerUserLimit())
                .startDate(startDate)
                .endDate(endDate)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .vendor(vendor)
                .build();

        return toDto(promotionRepository.save(promotion), 0L);
    }

    @Transactional
    public PromotionResponseDto updatePromotion(String publicPromotionId, PromotionRequestDto request) {
        Promotion promotion = promotionRepository.findByPublicPromotionId(publicPromotionId)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found"));

        // Only check code uniqueness if it changed
        String newCode = request.getCode().toUpperCase().trim();
        if (!newCode.equals(promotion.getCode()) && promotionRepository.existsByCode(newCode)) {
            throw new IllegalArgumentException("Promo code already exists: " + request.getCode());
        }

        LocalDateTime updStartDate = FlexibleLocalDateTimeDeserializer.parse(request.getStartDate());
        LocalDateTime updEndDate   = FlexibleLocalDateTimeDeserializer.parse(request.getEndDate());

        if (updEndDate.isBefore(updStartDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        VendorProfile vendor = null;
        if (request.getVendorPublicId() != null && !request.getVendorPublicId().isBlank()) {
            vendor = vendorProfileRepository.findByPublicVendorId(request.getVendorPublicId())
                    .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));
        }

        promotion.setCode(request.getCode().toUpperCase().trim());
        promotion.setTitle(request.getTitle());
        promotion.setDescription(request.getDescription());
        promotion.setType(request.getType());
        promotion.setValue(resolveValue(request));
        promotion.setMaxDiscountAmount(resolveMaxDiscount(request.getMaxDiscountAmount()));
        promotion.setMinimumOrderAmount(request.getMinimumOrderAmount());
        promotion.setUsageLimit(request.getUsageLimit());
        promotion.setPerUserLimit(request.getPerUserLimit());
        promotion.setStartDate(updStartDate);
        promotion.setEndDate(updEndDate);
        if (request.getIsActive() != null) {
            promotion.setIsActive(request.getIsActive());
        }
        promotion.setVendor(vendor);

        long usageCount = promotionUsageRepository.countByPromotion(promotion);
        return toDto(promotionRepository.save(promotion), usageCount);
    }

    @Transactional
    public void deactivatePromotion(String publicPromotionId) {
        Promotion promotion = promotionRepository.findByPublicPromotionId(publicPromotionId)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found"));
        promotion.setIsActive(false);
        promotionRepository.save(promotion);
    }

    @Transactional(readOnly = true)
    public List<PromotionResponseDto> getAllPromotions() {
        return promotionRepository.findAll().stream()
                .map(p -> toDto(p, promotionUsageRepository.countByPromotion(p)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PromotionResponseDto getPromotionByPublicId(String publicPromotionId) {
        Promotion promotion = promotionRepository.findByPublicPromotionId(publicPromotionId)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found"));
        return toDto(promotion, promotionUsageRepository.countByPromotion(promotion));
    }

    // ========== VENDOR METHODS ==========

    @Transactional
    public PromotionResponseDto createVendorPromotion(String username, PromotionRequestDto request) {
        VendorProfile vendor = vendorProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));

        if (promotionRepository.existsByCode(request.getCode().toUpperCase().trim())) {
            throw new IllegalArgumentException("Promo code already exists: " + request.getCode());
        }
        LocalDateTime vendorStartDate = FlexibleLocalDateTimeDeserializer.parse(request.getStartDate());
        LocalDateTime vendorEndDate   = FlexibleLocalDateTimeDeserializer.parse(request.getEndDate());
        if (vendorEndDate.isBefore(vendorStartDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        BigDecimal resolvedVendorValue = resolveValue(request);

        Promotion promotion = Promotion.builder()
                .code(request.getCode().toUpperCase().trim())
                .title(request.getTitle())
                .description(request.getDescription())
                .type(request.getType())
                .value(resolvedVendorValue)
                .maxDiscountAmount(resolveMaxDiscount(request.getMaxDiscountAmount()))
                .minimumOrderAmount(request.getMinimumOrderAmount())
                .usageLimit(request.getUsageLimit())
                .perUserLimit(request.getPerUserLimit())
                .startDate(vendorStartDate)
                .endDate(vendorEndDate)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .vendor(vendor)
                .build();

        return toDto(promotionRepository.save(promotion), 0L);
    }

    @Transactional
    public PromotionResponseDto updateVendorPromotion(String username, String publicPromotionId,
                                                      PromotionRequestDto request) {
        VendorProfile vendor = vendorProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));

        Promotion promotion = promotionRepository.findByPublicPromotionId(publicPromotionId)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found"));

        if (promotion.getVendor() == null || !promotion.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You do not have permission to modify this promotion");
        }

        String newCode = request.getCode().toUpperCase().trim();
        if (!newCode.equals(promotion.getCode()) && promotionRepository.existsByCode(newCode)) {
            throw new IllegalArgumentException("Promo code already exists: " + request.getCode());
        }
        LocalDateTime vendorUpdStart = FlexibleLocalDateTimeDeserializer.parse(request.getStartDate());
        LocalDateTime vendorUpdEnd   = FlexibleLocalDateTimeDeserializer.parse(request.getEndDate());
        if (vendorUpdEnd.isBefore(vendorUpdStart)) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        promotion.setCode(request.getCode().toUpperCase().trim());
        promotion.setTitle(request.getTitle());
        promotion.setDescription(request.getDescription());
        promotion.setType(request.getType());
        promotion.setValue(resolveValue(request));
        promotion.setMaxDiscountAmount(resolveMaxDiscount(request.getMaxDiscountAmount()));
        promotion.setMinimumOrderAmount(request.getMinimumOrderAmount());
        promotion.setUsageLimit(request.getUsageLimit());
        promotion.setPerUserLimit(request.getPerUserLimit());
        promotion.setStartDate(vendorUpdStart);
        promotion.setEndDate(vendorUpdEnd);
        if (request.getIsActive() != null) {
            promotion.setIsActive(request.getIsActive());
        }

        long usageCount = promotionUsageRepository.countByPromotion(promotion);
        return toDto(promotionRepository.save(promotion), usageCount);
    }

    @Transactional
    public void deactivateVendorPromotion(String username, String publicPromotionId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));

        Promotion promotion = promotionRepository.findByPublicPromotionId(publicPromotionId)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found"));

        if (promotion.getVendor() == null || !promotion.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You do not have permission to modify this promotion");
        }

        promotion.setIsActive(false);
        promotionRepository.save(promotion);
    }

    @Transactional(readOnly = true)
    public List<PromotionResponseDto> getVendorOwnPromotions(String username) {
        VendorProfile vendor = vendorProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));

        return promotionRepository.findByVendor(vendor).stream()
                .map(p -> toDto(p, promotionUsageRepository.countByPromotion(p)))
                .collect(Collectors.toList());
    }

    // ========== CUSTOMER METHODS ==========

    @Transactional(readOnly = true)
    public List<PromotionResponseDto> getActivePromotions() {
        return promotionRepository.findAllCurrentlyActive(LocalDateTime.now()).stream()
                .map(p -> toDto(p, null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PromotionResponseDto> getActivePromotionsForVendor(String vendorPublicId) {
        return promotionRepository.findActiveForVendor(LocalDateTime.now(), vendorPublicId).stream()
                .map(p -> toDto(p, null))
                .collect(Collectors.toList());
    }

    /**
     * Validate a promo code and return a preview of the discount that would apply.
     * Does NOT record usage.
     */
    @Transactional(readOnly = true)
    public PromotionResponseDto validateCode(String code) {
        Promotion promotion = promotionRepository.findByCode(code.toUpperCase().trim())
                .orElseThrow(() -> new EntityNotFoundException("Promo code not found"));
        if (!promotion.isCurrentlyActive()) {
            throw new IllegalStateException("Promo code is not currently active");
        }
        return toDto(promotion, null);
    }

    // ========== PREVIEW ==========

    /**
     * Validate a promo code and compute the exact discount for a given subtotal.
     * Safe to call before checkout — does NOT record usage.
     */
    @Transactional(readOnly = true)
    public PromotionPreviewResponseDto previewDiscount(PromotionPreviewRequestDto request,
                                                       String userPublicId) {
        Promotion promotion = promotionRepository.findByCode(request.getPromoCode().toUpperCase().trim())
                .orElseThrow(() -> new EntityNotFoundException("Promo code not found"));

        if (!promotion.isCurrentlyActive()) {
            throw new IllegalStateException("Promo code is expired or inactive");
        }

        // Vendor restriction check
        if (promotion.getVendor() != null &&
                !promotion.getVendor().getUser().getPublicUserId().equals(request.getVendorPublicId())) {
            throw new IllegalStateException("Promo code is not valid for this vendor");
        }

        // Minimum order amount check
        if (promotion.getMinimumOrderAmount() != null &&
                request.getSubtotal().compareTo(promotion.getMinimumOrderAmount()) < 0) {
            throw new IllegalStateException(String.format(
                    "Minimum order amount of $%.2f required for this promo code",
                    promotion.getMinimumOrderAmount()));
        }

        // Global usage limit check
        if (promotion.getUsageLimit() != null) {
            long totalUsed = promotionUsageRepository.countByPromotion(promotion);
            if (totalUsed >= promotion.getUsageLimit()) {
                throw new IllegalStateException("Promo code has reached its usage limit");
            }
        }

        // Per-user limit check
        if (promotion.getPerUserLimit() != null && userPublicId != null) {
            User user = userRepository.findByPublicUserId(userPublicId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
            long userUsed = promotionUsageRepository.countByPromotionAndUser(promotion, user);
            if (userUsed >= promotion.getPerUserLimit()) {
                throw new IllegalStateException("You have already used this promo code the maximum number of times");
            }
        }

        BigDecimal discountAmount = computeDiscountAmount(promotion, request.getSubtotal(), request.getDeliveryFee());
        BigDecimal discountedSubtotal = request.getSubtotal().subtract(discountAmount);

        return PromotionPreviewResponseDto.builder()
                .promoCode(promotion.getCode())
                .title(promotion.getTitle())
                .description(promotion.getDescription())
                .type(promotion.getType())
                .value(promotion.getValue())
                .discountAmount(discountAmount)
                .discountedSubtotal(discountedSubtotal)
                .build();
    }

    // ========== ORDER INTEGRATION ==========

    /**
     * Validate and calculate discount for a promo code applied to an order subtotal.
     * Call this before saving the order. Does NOT record usage.
     *
     * @param code          promo code from the request
     * @param subtotal      order subtotal (before discount, delivery fee, tax)
     * @param userPublicId  authenticated customer's public user ID
     * @param vendorPublicId vendor the order is placed with
     * @return discount amount to subtract from the order total
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateDiscount(String code, BigDecimal subtotal,
                                        String userPublicId, String vendorPublicId) {
        Promotion promotion = promotionRepository.findByCode(code.toUpperCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid promo code: " + code));

        if (!promotion.isCurrentlyActive()) {
            throw new IllegalStateException("Promo code is expired or inactive");
        }

        // Vendor restriction check
        if (promotion.getVendor() != null &&
                !promotion.getVendor().getPublicVendorId().equals(vendorPublicId)) {
            throw new IllegalStateException("Promo code is not valid for this vendor");
        }

        // Minimum order amount check
        if (promotion.getMinimumOrderAmount() != null &&
                subtotal.compareTo(promotion.getMinimumOrderAmount()) < 0) {
            throw new IllegalStateException(String.format(
                    "Minimum order amount of $%.2f required for this promo code",
                    promotion.getMinimumOrderAmount()));
        }

        // Global usage limit check
        if (promotion.getUsageLimit() != null) {
            long totalUsed = promotionUsageRepository.countByPromotion(promotion);
            if (totalUsed >= promotion.getUsageLimit()) {
                throw new IllegalStateException("Promo code has reached its usage limit");
            }
        }

        // Per-user limit check
        if (promotion.getPerUserLimit() != null) {
            User user = userRepository.findByPublicUserId(userPublicId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
            long userUsed = promotionUsageRepository.countByPromotionAndUser(promotion, user);
            if (userUsed >= promotion.getPerUserLimit()) {
                throw new IllegalStateException("You have already used this promo code the maximum number of times");
            }
        }

        return computeDiscountAmount(promotion, subtotal);
    }

    /**
     * Record that a promo code was used for an order.
     * Call this after the order is saved and payment confirmed.
     */
    @Transactional
    public void recordUsage(String code, User user, Order order, BigDecimal discountApplied) {
        Promotion promotion = promotionRepository.findByCode(code.toUpperCase().trim())
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found"));

        PromotionUsage usage = PromotionUsage.builder()
                .promotion(promotion)
                .user(user)
                .order(order)
                .discountApplied(discountApplied)
                .build();

        promotionUsageRepository.save(usage);
    }

    // ========== PRIVATE HELPERS ==========

    /**
     * Treats a {@code maxDiscountAmount} of zero as "no cap" (null).
     * The frontend sends 0 when the user leaves the field blank.
     */
    private BigDecimal resolveMaxDiscount(BigDecimal raw) {
        return (raw == null || raw.compareTo(BigDecimal.ZERO) == 0) ? null : raw;
    }

    /**
     * Resolves the discount value for a promotion request.
     * <ul>
     *   <li>FREE_DELIVERY — value is not applicable; store {@code BigDecimal.ZERO}.</li>
     *   <li>PERCENTAGE / FIXED_AMOUNT — value is mandatory; throws if absent.</li>
     * </ul>
     */
    private BigDecimal resolveValue(PromotionRequestDto request) {
        if (request.getType() == PromotionType.FREE_DELIVERY) {
            return BigDecimal.ZERO;
        }
        if (request.getValue() == null) {
            throw new IllegalArgumentException(
                    "Value is required for " + request.getType() + " promotions");
        }
        if (request.getType() == PromotionType.PERCENTAGE &&
                request.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage value cannot exceed 100");
        }
        return request.getValue();
    }

    private BigDecimal computeDiscountAmount(Promotion promotion, BigDecimal subtotal) {
        return computeDiscountAmount(promotion, subtotal, null);
    }

    private BigDecimal computeDiscountAmount(Promotion promotion, BigDecimal subtotal,
                                             BigDecimal deliveryFee) {
        BigDecimal discount;
        if (promotion.getType() == PromotionType.PERCENTAGE) {
            discount = subtotal
                    .multiply(promotion.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (promotion.getMaxDiscountAmount() != null &&
                    discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
                discount = promotion.getMaxDiscountAmount();
            }
        } else if (promotion.getType() == PromotionType.FREE_DELIVERY) {
            // Discount equals the delivery fee — zero for pickup orders
            discount = deliveryFee != null
                    ? deliveryFee.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        } else {
            // FIXED_AMOUNT
            discount = promotion.getValue() != null
                    ? promotion.getValue().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }
        // Discount cannot exceed the subtotal
        return discount.min(subtotal);
    }

    private PromotionResponseDto toDto(Promotion promotion, Long usageCount) {
        return PromotionResponseDto.builder()
                .publicPromotionId(promotion.getPublicPromotionId())
                .code(promotion.getCode())
                .title(promotion.getTitle())
                .description(promotion.getDescription())
                .type(promotion.getType())
                .value(promotion.getValue())
                .maxDiscountAmount(promotion.getMaxDiscountAmount())
                .minimumOrderAmount(promotion.getMinimumOrderAmount())
                .usageLimit(promotion.getUsageLimit())
                .perUserLimit(promotion.getPerUserLimit())
                .startDate(promotion.getStartDate())
                .endDate(promotion.getEndDate())
                .isActive(promotion.getIsActive())
                .isCurrentlyActive(promotion.isCurrentlyActive())
                .vendorPublicId(promotion.getVendor() != null
                        ? promotion.getVendor().getPublicVendorId() : null)
                .vendorName(promotion.getVendor() != null
                        ? promotion.getVendor().getRestaurantName() : null)
                .totalUsageCount(usageCount)
                .createdAt(promotion.getCreatedAt())
                .updatedAt(promotion.getUpdatedAt())
                .build();
    }
}
