package com.afrochow.order.repository;

import com.afrochow.order.model.Order;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.common.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByPublicOrderId(String publicOrderId);

    // Customer queries
    List<Order> findByCustomer(CustomerProfile customer);

    List<Order> findByCustomerOrderByOrderTimeDesc(CustomerProfile customer);

    List<Order> findByCustomerAndStatus(CustomerProfile customer, OrderStatus status);

    // Vendor queries
    List<Order> findByVendor(VendorProfile vendor);

    List<Order> findByVendorOrderByOrderTimeDesc(VendorProfile vendor);

    List<Order> findByVendorAndStatus(VendorProfile vendor, OrderStatus status);

    // Status queries
    List<Order> findByStatus(OrderStatus status);

    List<Order> findByStatusOrderByOrderTimeDesc(OrderStatus status);

    // Active orders queries
    @Query("SELECT o FROM Order o WHERE o.status NOT IN ('DELIVERED', 'CANCELLED', 'REFUNDED') ORDER BY o.orderTime DESC")
    List<Order> findActiveOrders();

    @Query("SELECT o FROM Order o WHERE o.customer = :customer AND o.status NOT IN ('DELIVERED', 'CANCELLED', 'REFUNDED') ORDER BY o.orderTime DESC")
    List<Order> findActiveOrdersByCustomer(@Param("customer") CustomerProfile customer);

    @Query("SELECT o FROM Order o WHERE o.vendor = :vendor AND o.status NOT IN ('DELIVERED', 'CANCELLED', 'REFUNDED') ORDER BY o.orderTime DESC")
    List<Order> findActiveOrdersByVendor(@Param("vendor") VendorProfile vendor);

    // Date range queries
    List<Order> findByOrderTimeBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<Order> findByVendorAndOrderTimeBetween(VendorProfile vendor, LocalDateTime startDate, LocalDateTime endDate);

    // Today's orders
    @Query("SELECT o FROM Order o WHERE DATE(o.orderTime) = CURRENT_DATE ORDER BY o.orderTime DESC")
    List<Order> findTodayOrders();

    @Query("SELECT o FROM Order o WHERE o.vendor = :vendor AND DATE(o.orderTime) = CURRENT_DATE ORDER BY o.orderTime DESC")
    List<Order> findTodayOrdersByVendor(@Param("vendor") VendorProfile vendor);

    // Revenue calculations
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'DELIVERED'")
    BigDecimal calculateTotalRevenue();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.vendor = :vendor AND o.status = 'DELIVERED'")
    BigDecimal calculateVendorRevenue(@Param("vendor") VendorProfile vendor);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.vendor = :vendor AND o.status = 'DELIVERED' AND DATE(o.orderTime) = CURRENT_DATE")
    BigDecimal calculateVendorTodayRevenue(@Param("vendor") VendorProfile vendor);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.vendor = :vendor AND o.status = 'DELIVERED' AND o.orderTime >= :startDate")
    BigDecimal calculateVendorRevenueFromDate(@Param("vendor") VendorProfile vendor, @Param("startDate") LocalDateTime startDate);

    // Count queries
    Long countByCustomer(CustomerProfile customer);

    Long countByVendor(VendorProfile vendor);

    Long countByStatus(OrderStatus status);

    Long countByVendorAndStatus(VendorProfile vendor, OrderStatus status);

    Long countByVendorId(Long vendorId);

    @Query("SELECT COUNT(o) FROM Order o WHERE DATE(o.orderTime) = CURRENT_DATE")
    Long countTodayOrders();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.vendor = :vendor AND DATE(o.orderTime) = CURRENT_DATE")
    Long countVendorTodayOrders(@Param("vendor") VendorProfile vendor);

    // Sum aggregation queries
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.vendor.id = :vendorId")
    BigDecimal sumTotalAmountByVendorId(@Param("vendorId") Long vendorId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.vendor.id = :vendorId AND o.status = :status")
    Optional<BigDecimal> sumTotalAmountByVendorIdAndStatus(
            @Param("vendorId") Long vendorId,
            @Param("status") OrderStatus status
    );

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status")
    Optional<BigDecimal> sumTotalAmountByStatus(@Param("status") OrderStatus status);
}