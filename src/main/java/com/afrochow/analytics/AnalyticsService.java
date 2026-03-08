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

    // ================= VENDOR ANALYTICS =================

    public VendorAnalytics getVendorAnalytics(String vendorPublicId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        return VendorAnalytics.builder()
                .totalOrders(orderRepository.countByVendor(vendor))
                .deliveredOrders(orderRepository.countByVendorAndStatus(vendor, OrderStatus.DELIVERED))
                .cancelledOrders(orderRepository.countByVendorAndStatus(vendor, OrderStatus.CANCELLED))
                .pendingOrders((long) orderRepository.findActiveOrdersByVendor(vendor).size())
                .todayOrders(orderRepository.countVendorTodayOrders(vendor))
                .totalRevenue(nvl(orderRepository.calculateVendorRevenue(vendor)))
                .todayRevenue(nvl(orderRepository.calculateVendorTodayRevenue(vendor)))
                .last7DaysRevenue(nvl(orderRepository.calculateVendorRevenueFromDate(vendor, LocalDateTime.now().minusDays(7))))
                .last30DaysRevenue(nvl(orderRepository.calculateVendorRevenueFromDate(vendor, LocalDateTime.now().minusDays(30))))
                .totalProducts(productRepository.countByVendor(vendor))
                .activeProducts(productRepository.countByVendorAndAvailable(vendor, true))
                .totalReviews(reviewRepository.countByVendor(vendor))
                .averageRating(round(reviewRepository.calculateVendorAverageRating(vendor)))
                .build();
    }

    public VendorSalesReport getVendorSalesReport(String vendorPublicId, LocalDateTime start, LocalDateTime end) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        List<Order> orders = orderRepository.findByVendorAndOrderTimeBetween(vendor, start, end);

        BigDecimal revenue = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return VendorSalesReport.builder()
                .startDate(start)
                .endDate(end)
                .totalOrders((long) orders.size())
                .deliveredOrders(orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count())
                .totalRevenue(revenue)
                .averageOrderValue(orders.isEmpty() ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP))
                .build();
    }

    public List<PopularProduct> getVendorPopularProducts(String vendorPublicId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
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

    public CustomerAnalytics getCustomerAnalytics(String customerPublicId) {
        User user = userRepository.findByPublicUserId(customerPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        List<Order> orders = orderRepository.findByCustomer(customer);

        BigDecimal spent = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CustomerAnalytics.builder()
                .totalOrders((long) orders.size())
                .deliveredOrders(orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count())
                .cancelledOrders(orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count())
                .totalSpent(spent)
                .averageOrderValue(orders.isEmpty() ? BigDecimal.ZERO : spent.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP))
                .totalReviews((long) reviewRepository.findByUser(user).size())
                .build();
    }

    public CustomerOrderHistory getCustomerOrderHistory(String customerPublicId) {
        User user = userRepository.findByPublicUserId(customerPublicId)
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
                                .totalAmount(o.getTotalAmount())
                                .orderTime(o.getOrderTime())
                                .build())
                        .toList())
                .build();
    }

    // ================= ADMIN ANALYTICS =================

    public AdminAnalytics getAdminAnalytics() {
        return AdminAnalytics.builder()
                .totalUsers(userRepository.count())
                .totalCustomers((long) userRepository.findByRole(Role.CUSTOMER).size())
                .totalVendors((long) userRepository.findByRole(Role.VENDOR).size())
                .activeUsers((long) userRepository.findByIsActive(true).size())
                .totalOrders(orderRepository.count())
                .deliveredOrders(orderRepository.countByStatus(OrderStatus.DELIVERED))
                .cancelledOrders(orderRepository.countByStatus(OrderStatus.CANCELLED))
                .pendingOrders((long) orderRepository.findActiveOrders().size())
                .todayOrders(orderRepository.countTodayOrders())
                .totalRevenue(nvl(orderRepository.calculateTotalRevenue()))
                .totalProducts(productRepository.count())
                .availableProducts(productRepository.countByAvailable(true))
                .successfulPayments(paymentRepository.countByStatus(PaymentStatus.COMPLETED))
                .failedPayments(paymentRepository.countByStatus(PaymentStatus.FAILED))
                .totalReviews(reviewRepository.count())
                .build();
    }

    public PlatformTrends getPlatformTrends() {
        LocalDateTime now = LocalDateTime.now();

        List<Order> last7DaysOrders = orderRepository.findByOrderTimeBetween(now.minusDays(7), now);
        List<Order> last30DaysOrders = orderRepository.findByOrderTimeBetween(now.minusDays(30), now);

        BigDecimal revenue7Days = last7DaysOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal revenue30Days = last30DaysOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PlatformTrends.builder()
                .ordersLast7Days((long) last7DaysOrders.size())
                .ordersLast30Days((long) last30DaysOrders.size())
                .revenueLast7Days(revenue7Days)
                .revenueLast30Days(revenue30Days)
                .build();
    }

    // ================= HELPERS =================

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Double round(Double value) {
        return value == null ? 0.0 : Math.round(value * 10.0) / 10.0;
    }

    // ================= DTOs =================

    @Data @Builder
    public static class VendorAnalytics {
        private Long totalOrders, deliveredOrders, cancelledOrders, pendingOrders, todayOrders;
        private BigDecimal totalRevenue, todayRevenue, last7DaysRevenue, last30DaysRevenue;
        private Long totalProducts, activeProducts, totalReviews;
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
        private BigDecimal totalAmount;
        private LocalDateTime orderTime;
    }

    @Data @Builder
    public static class AdminAnalytics {
        private Long totalUsers, totalCustomers, totalVendors, activeUsers;
        private Long totalOrders, deliveredOrders, cancelledOrders, pendingOrders, todayOrders;
        private BigDecimal totalRevenue;
        private Long totalProducts, availableProducts, successfulPayments, failedPayments, totalReviews;
    }

    @Data @Builder
    public static class PlatformTrends {
        private Long ordersLast7Days, ordersLast30Days;
        private BigDecimal revenueLast7Days, revenueLast30Days;
    }
}
