package com.afrochow.stats.service;

import com.afrochow.common.enums.Role;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.review.model.Review;
import com.afrochow.review.repository.ReviewRepository;
import com.afrochow.stats.dto.PlatformStatsDto;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for calculating platform-wide statistics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatsService {

    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;

    /**
     * Get platform statistics
     * Cached for 5 minutes to reduce database load
     */
    @Cacheable(value = "platformStats", unless = "#result == null")
    @Transactional(readOnly = true)
    public PlatformStatsDto getPlatformStats() {
        log.info("Calculating platform statistics");

        // Vendor stats
        Long totalVendors = vendorProfileRepository.count();
        Long totalActiveVendors = vendorProfileRepository.countByIsActiveTrue();
        Long totalVerifiedVendors = vendorProfileRepository.countByIsVerifiedTrueAndIsActiveTrue();

        // Customer stats (verified customers only)
        Long totalCustomers = userRepository.countByRoleAndEmailVerifiedTrue(Role.CUSTOMER);
        Long totalActiveCustomers = userRepository.countByRoleAndIsActiveTrue(Role.CUSTOMER);

        // Product stats
        Long totalProducts = productRepository.count();
        Long totalAvailableProducts = productRepository.countByAvailableTrue();

        // Order stats
        Long totalOrders = orderRepository.count();
        Long totalCompletedOrders = orderRepository.countByStatus(
                com.afrochow.common.enums.OrderStatus.DELIVERED
        );

        // Average delivery time (from products' preparation time)
        Integer avgDeliveryTime = calculateAverageDeliveryTime();

        // Review stats
        Long totalReviews = reviewRepository.count();
        Double avgPlatformRating = calculateAveragePlatformRating();

        return PlatformStatsDto.builder()
                .totalVendors(totalVendors)
                .totalActiveVendors(totalActiveVendors)
                .totalVerifiedVendors(totalVerifiedVendors)
                .totalCustomers(totalCustomers)
                .totalActiveCustomers(totalActiveCustomers)
                .totalProducts(totalProducts)
                .totalAvailableProducts(totalAvailableProducts)
                .totalOrders(totalOrders)
                .totalCompletedOrders(totalCompletedOrders)
                .averageDeliveryTimeMinutes(avgDeliveryTime)
                .totalReviews(totalReviews)
                .averagePlatformRating(avgPlatformRating)
                .build();
    }

    /**
     * Calculate average delivery time from product preparation times
     */
    private Integer calculateAverageDeliveryTime() {
        try {
            double avg = productRepository.findAll().stream()
                    .filter(p -> p.getPreparationTimeMinutes() != null && p.getPreparationTimeMinutes() > 0)
                    .mapToInt(Product::getPreparationTimeMinutes)
                    .average()
                    .orElse(30.0);

            // Add 10 minutes for delivery time
            return (int) Math.round(avg) + 10;
        } catch (Exception e) {
            log.error("Error calculating average delivery time", e);
            return 30; // Default fallback
        }
    }

    /**
     * Calculate average platform rating from all reviews
     */
    private Double calculateAveragePlatformRating() {
        try {
            return reviewRepository.findAll().stream()
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(4.5);
        } catch (Exception e) {
            log.error("Error calculating average platform rating", e);
            return 4.5; // Default fallback
        }
    }
}
