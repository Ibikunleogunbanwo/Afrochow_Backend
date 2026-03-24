package com.afrochow.analytics;

import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.customer.repository.CustomerProfileRepository;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.common.enums.Role;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.payment.repository.PaymentRepository;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.promotion.repository.PromotionRepository;
import com.afrochow.promotion.repository.PromotionUsageRepository;
import com.afrochow.review.repository.ReviewRepository;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;

    // ================= VENDOR ANALYTICS =================

    public VendorAnalytics getVendorAnalytics(String username) {
        VendorProfile vendor = vendorProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        return VendorAnalytics.builder()
                .totalOrders(orderRepository.countByVendor(vendor))
                // Pipeline breakdown — each status is a distinct vendor action point
                .pendingOrders(orderRepository.countByVendorAndStatus(vendor, OrderStatus.PENDING))
                .confirmedOrders(orderRepository.countByVendorAndStatus(vendor, OrderStatus.CONFIRMED))
                .preparingOrders(orderRepository.countByVendorAndStatus(vendor, OrderStatus.PREPARING))
                .readyOrders(orderRepository.countByVendorAndStatus(vendor, OrderStatus.READY_FOR_PICKUP))
                .outForDeliveryOrders(orderRepository.countByVendorAndStatus(vendor, OrderStatus.OUT_FOR_DELIVERY))
                .deliveredOrders(orderRepository.countByVendorAndStatus(vendor, OrderStatus.DELIVERED))
                .cancelledOrders(orderRepository.countByVendorAndStatus(vendor, OrderStatus.CANCELLED))
                .activeOrders(orderRepository.countActiveOrdersByVendor(vendor))
                .todayOrders(orderRepository.countVendorTodayOrders(vendor))
                // Revenue (DELIVERED orders only)
                .totalRevenue(nvl(orderRepository.calculateVendorRevenue(vendor)))
                .todayRevenue(nvl(orderRepository.calculateVendorTodayRevenue(vendor)))
                .last7DaysRevenue(nvl(orderRepository.calculateVendorRevenueFromDate(vendor, LocalDateTime.now().minusDays(7))))
                .last30DaysRevenue(nvl(orderRepository.calculateVendorRevenueFromDate(vendor, LocalDateTime.now().minusDays(30))))
                // Catalog
                .totalProducts(productRepository.countByVendor(vendor))
                .activeProducts(productRepository.countByVendorAndAvailable(vendor, true))
                // Reviews
                .totalReviews(reviewRepository.countByVendor(vendor))
                .averageRating(round(reviewRepository.calculateVendorAverageRating(vendor)))
                .build();
    }

    public VendorSalesReport getVendorSalesReport(String username, LocalDateTime start, LocalDateTime end) {
        VendorProfile vendor = vendorProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        List<Order> orders = orderRepository.findByVendorAndOrderTimeBetween(vendor, start, end);

        long deliveredCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();

        BigDecimal revenue = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Average order value = revenue ÷ delivered orders (not all orders in range)
        BigDecimal avgOrderValue = deliveredCount == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(deliveredCount), 2, RoundingMode.HALF_UP);

        return VendorSalesReport.builder()
                .startDate(start)
                .endDate(end)
                .totalOrders((long) orders.size())
                .deliveredOrders(deliveredCount)
                .totalRevenue(revenue)
                .averageOrderValue(avgOrderValue)
                .build();
    }

    public List<PopularProduct> getVendorPopularProducts(String username) {
        VendorProfile vendor = vendorProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        return productRepository.findByVendor(vendor).stream()
                .map(p -> PopularProduct.builder()
                        .productPublicId(p.getPublicProductId())
                        .productName(p.getName())
                        .orderCount(p.getOrderLines() == null ? 0 : p.getOrderLines().size())
                        .reviewCount(p.getReviews() == null ? 0 : p.getReviews().size())
                        .averageRating(reviewRepository.calculateProductAverageRating(p))
                        .build())
                .sorted((a, b) -> Integer.compare(b.orderCount, a.orderCount))
                .limit(10)
                .toList();
    }

    // ================= CUSTOMER ANALYTICS =================

    public CustomerAnalytics getCustomerAnalytics(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        long totalOrders = orderRepository.countByCustomer(customer);
        long deliveredOrders = orderRepository.countByCustomerAndStatus(customer, OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByCustomerAndStatus(customer, OrderStatus.CANCELLED);
        BigDecimal totalSpent = nvl(orderRepository.calculateCustomerRevenue(customer));

        // Average order value = spent ÷ delivered orders (spent is DELIVERED only)
        BigDecimal avgOrderValue = deliveredOrders == 0
                ? BigDecimal.ZERO
                : totalSpent.divide(BigDecimal.valueOf(deliveredOrders), 2, RoundingMode.HALF_UP);

        return CustomerAnalytics.builder()
                .totalOrders(totalOrders)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .totalSpent(totalSpent)
                .averageOrderValue(avgOrderValue)
                .totalReviews((long) reviewRepository.findByUser(user).size())
                .build();
    }

    public CustomerOrderHistory getCustomerOrderHistory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        List<Order> orders = orderRepository.findByCustomerOrderByOrderTimeDesc(customer);

        return CustomerOrderHistory.builder()
                .totalOrders((long) orders.size())
                .recentOrders(orders.stream()
                        .limit(10)
                        .map(o -> OrderSummary.builder()
                                .orderPublicId(o.getPublicOrderId())
                                .vendorName(o.getVendor().getRestaurantName())
                                .status(o.getStatus())
                                .statusLabel(resolveStatusLabel(o.getStatus(), o.getFulfillmentType()))
                                .fulfillmentType(o.getFulfillmentType())
                                .totalAmount(o.getTotalAmount())
                                .discount(o.getDiscount())
                                .appliedPromoCode(o.getAppliedPromoCode())
                                .orderTime(o.getOrderTime())
                                .build())
                        .toList())
                .build();
    }

    // ================= ADMIN ANALYTICS =================

    /**
     * Returns platform-wide analytics. When startDate and endDate are both provided,
     * all transactional metrics (orders, revenue, payments, reviews) are scoped to
     * that window. User, product and promotion counts always reflect current state.
     */
    public AdminAnalytics getAdminAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        boolean isFiltered = startDate != null && endDate != null;

        return AdminAnalytics.builder()
                // Users — always current state (not date-filtered)
                .totalUsers(userRepository.count())
                .totalCustomers(userRepository.countByRole(Role.CUSTOMER))
                .totalVendors(userRepository.countByRole(Role.VENDOR))
                .activeUsers((long) userRepository.findByIsActive(true).size())

                // Orders — filtered when date range provided
                .totalOrders(isFiltered
                        ? orderRepository.countOrdersBetween(startDate, endDate)
                        : orderRepository.count())
                .pendingOrders(isFiltered
                        ? orderRepository.countByStatusAndOrderTimeBetween(OrderStatus.PENDING, startDate, endDate)
                        : orderRepository.countByStatus(OrderStatus.PENDING))
                .confirmedOrders(isFiltered
                        ? orderRepository.countByStatusAndOrderTimeBetween(OrderStatus.CONFIRMED, startDate, endDate)
                        : orderRepository.countByStatus(OrderStatus.CONFIRMED))
                .preparingOrders(isFiltered
                        ? orderRepository.countByStatusAndOrderTimeBetween(OrderStatus.PREPARING, startDate, endDate)
                        : orderRepository.countByStatus(OrderStatus.PREPARING))
                .readyOrders(isFiltered
                        ? orderRepository.countByStatusAndOrderTimeBetween(OrderStatus.READY_FOR_PICKUP, startDate, endDate)
                        : orderRepository.countByStatus(OrderStatus.READY_FOR_PICKUP))
                .outForDeliveryOrders(isFiltered
                        ? orderRepository.countByStatusAndOrderTimeBetween(OrderStatus.OUT_FOR_DELIVERY, startDate, endDate)
                        : orderRepository.countByStatus(OrderStatus.OUT_FOR_DELIVERY))
                .deliveredOrders(isFiltered
                        ? orderRepository.countByStatusAndOrderTimeBetween(OrderStatus.DELIVERED, startDate, endDate)
                        : orderRepository.countByStatus(OrderStatus.DELIVERED))
                .cancelledOrders(isFiltered
                        ? orderRepository.countByStatusAndOrderTimeBetween(OrderStatus.CANCELLED, startDate, endDate)
                        : orderRepository.countByStatus(OrderStatus.CANCELLED))
                .refundedOrders(isFiltered
                        ? orderRepository.countByStatusAndOrderTimeBetween(OrderStatus.REFUNDED, startDate, endDate)
                        : orderRepository.countByStatus(OrderStatus.REFUNDED))
                .activeOrders(isFiltered
                        ? orderRepository.countActiveOrdersBetween(startDate, endDate)
                        : orderRepository.countActiveOrders())
                .todayOrders(orderRepository.countTodayOrders())

                // Revenue — filtered when date range provided
                .totalRevenue(isFiltered
                        ? nvl(orderRepository.calculateRevenueBetween(startDate, endDate))
                        : nvl(orderRepository.calculateTotalRevenue()))

                // Catalog — always current state
                .totalProducts(productRepository.count())
                .availableProducts(productRepository.countByAvailable(true))

                // Payments — filtered when date range provided
                .successfulPayments(isFiltered
                        ? paymentRepository.countByStatusAndPaymentTimeBetween(PaymentStatus.COMPLETED, startDate, endDate)
                        : paymentRepository.countByStatus(PaymentStatus.COMPLETED))
                .failedPayments(isFiltered
                        ? paymentRepository.countByStatusAndPaymentTimeBetween(PaymentStatus.FAILED, startDate, endDate)
                        : paymentRepository.countByStatus(PaymentStatus.FAILED))

                // Reviews — filtered when date range provided
                .totalReviews(isFiltered
                        ? reviewRepository.countByCreatedAtBetween(startDate, endDate)
                        : reviewRepository.count())

                // Promotions — always current state
                .totalPromotions(promotionRepository.count())
                .activePromotions((long) promotionRepository.findAllCurrentlyActive(now).size())
                .totalDiscountGiven(nvl(promotionUsageRepository.calculateTotalDiscountGiven()))

                // Date range metadata (null when not filtered)
                .filterStartDate(startDate)
                .filterEndDate(endDate)
                .build();
    }

    /**
     * Returns platform trends. When startDate and endDate are both provided,
     * a custom-range bucket is included alongside the standard 7/30-day windows.
     */
    public PlatformTrends getPlatformTrends(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        boolean isFiltered = startDate != null && endDate != null;

        return PlatformTrends.builder()
                .ordersLast7Days(orderRepository.countOrdersBetween(sevenDaysAgo, now))
                .ordersLast30Days(orderRepository.countOrdersBetween(thirtyDaysAgo, now))
                .revenueLast7Days(nvl(orderRepository.calculateRevenueBetween(sevenDaysAgo, now)))
                .revenueLast30Days(nvl(orderRepository.calculateRevenueBetween(thirtyDaysAgo, now)))
                // Custom range — only populated when startDate/endDate provided
                .ordersInDateRange(isFiltered
                        ? orderRepository.countOrdersBetween(startDate, endDate)
                        : null)
                .revenueInDateRange(isFiltered
                        ? nvl(orderRepository.calculateRevenueBetween(startDate, endDate))
                        : null)
                .filterStartDate(startDate)
                .filterEndDate(endDate)
                .build();
    }

    // ================= HELPERS =================

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Double round(Double value) {
        return value == null ? 0.0 : Math.round(value * 10.0) / 10.0;
    }

    private static String resolveStatusLabel(OrderStatus status, String fulfillmentType) {
        if (status == null) return null;
        boolean isPickup = "PICKUP".equalsIgnoreCase(fulfillmentType);
        return switch (status) {
            case PENDING          -> "Awaiting Confirmation";
            case CONFIRMED        -> "Order Confirmed";
            case PREPARING        -> "Being Prepared";
            case READY_FOR_PICKUP -> isPickup ? "Available for Pickup" : "Ready for Delivery";
            case OUT_FOR_DELIVERY -> "Out for Delivery";
            case DELIVERED        -> isPickup ? "Picked Up" : "Delivered";
            case CANCELLED        -> "Cancelled";
            case REFUNDED         -> "Refunded";
        };
    }

    // ================= DTOs =================

    @Data @Builder
    public static class VendorAnalytics {
        private Long totalOrders;
        // Full pipeline breakdown
        private Long pendingOrders;       // awaiting vendor acceptance
        private Long confirmedOrders;     // accepted, not yet preparing
        private Long preparingOrders;
        private Long readyOrders;         // READY_FOR_PICKUP (pickup or delivery)
        private Long outForDeliveryOrders;
        private Long deliveredOrders;
        private Long cancelledOrders;
        private Long activeOrders;        // all non-terminal orders
        private Long todayOrders;
        // Revenue (DELIVERED orders only)
        private BigDecimal totalRevenue, todayRevenue, last7DaysRevenue, last30DaysRevenue;
        // Catalog
        private Long totalProducts, activeProducts;
        // Reviews
        private Long totalReviews;
        private Double averageRating;
    }

    @Data @Builder
    public static class VendorSalesReport {
        private LocalDateTime startDate, endDate;
        private Long totalOrders, deliveredOrders;
        private BigDecimal totalRevenue, averageOrderValue;
    }

    @Data @Builder
    public static class PopularProduct {
        private String productPublicId, productName;
        private Integer orderCount, reviewCount;
        private Double averageRating;
    }

    @Data @Builder
    public static class CustomerAnalytics {
        private Long totalOrders, deliveredOrders, cancelledOrders, totalReviews;
        private BigDecimal totalSpent, averageOrderValue;
    }

    @Data @Builder
    public static class CustomerOrderHistory {
        private Long totalOrders;
        private List<OrderSummary> recentOrders;
    }

    @Data @Builder
    public static class OrderSummary {
        private String orderPublicId;
        private String vendorName;
        private OrderStatus status;
        private String statusLabel;
        private String fulfillmentType;
        private BigDecimal totalAmount;
        private BigDecimal discount;
        private String appliedPromoCode;
        private LocalDateTime orderTime;
    }

    @Data @Builder
    public static class AdminAnalytics {
        private Long totalUsers, totalCustomers, totalVendors, activeUsers;
        // Full order pipeline
        private Long totalOrders;
        private Long pendingOrders;
        private Long confirmedOrders;
        private Long preparingOrders;
        private Long readyOrders;
        private Long outForDeliveryOrders;
        private Long deliveredOrders;
        private Long cancelledOrders;
        private Long refundedOrders;
        private Long activeOrders;
        private Long todayOrders;
        // Revenue
        private BigDecimal totalRevenue;
        // Catalog
        private Long totalProducts, availableProducts;
        // Payments
        private Long successfulPayments, failedPayments;
        // Reviews
        private Long totalReviews;
        // Promotions
        private Long totalPromotions, activePromotions;
        private BigDecimal totalDiscountGiven;
        // Active filter window — null when no filter applied
        private LocalDateTime filterStartDate;
        private LocalDateTime filterEndDate;
    }

    @Data @Builder
    public static class PlatformTrends {
        // Standard rolling windows (always populated)
        private Long ordersLast7Days, ordersLast30Days;
        private BigDecimal revenueLast7Days, revenueLast30Days;
        // Custom date range (only populated when startDate/endDate params are provided)
        private Long ordersInDateRange;
        private BigDecimal revenueInDateRange;
        private LocalDateTime filterStartDate;
        private LocalDateTime filterEndDate;
    }
}
