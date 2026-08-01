package com.afrochow.order.repository;

import com.afrochow.order.model.Order;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.common.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("SELECT o FROM Order o WHERE o.customer.user.userId = :customerUserId AND o.checkoutIdempotencyKey = :checkoutIdempotencyKey")
    Optional<Order> findByCustomerUserIdAndCheckoutIdempotencyKey(
            @Param("customerUserId") Long customerUserId,
            @Param("checkoutIdempotencyKey") String checkoutIdempotencyKey);

    /**
     * Fetch an order by its public ID with a database-level write lock.
     * Use this in any service method that transitions order status to prevent
     * two concurrent requests from both seeing the same "old" status and both
     * proceeding past the guard check (e.g. double-accept, double-cancel).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.publicOrderId = :publicOrderId")
    Optional<Order> findByPublicOrderIdWithLock(@Param("publicOrderId") String publicOrderId);

    /**
     * Fetch an order by its surrogate PK with a database-level write lock.
     * Use this in scheduler-driven transitions (autoExpire, autoDeliver) where
     * the caller already holds the Order object but needs to re-fetch with a lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderId = :id")
    Optional<Order> findByOrderIdWithLock(@Param("id") Long id);

    // Customer queries
    List<Order> findByCustomer(CustomerProfile customer);

    List<Order> findByCustomerOrderByOrderTimeDesc(CustomerProfile customer);

    List<Order> findByCustomerAndStatus(CustomerProfile customer, OrderStatus status);

    /** All delivered orders placed by a specific customer at a specific vendor. Used by review eligibility checks. */
    List<Order> findByCustomerAndVendorAndStatus(CustomerProfile customer, VendorProfile vendor, OrderStatus status);

    // Vendor queries
    List<Order> findByVendor(VendorProfile vendor);

    List<Order> findByVendorOrderByOrderTimeDesc(VendorProfile vendor);

    List<Order> findByVendorAndStatus(VendorProfile vendor, OrderStatus status);

    // Status queries
    List<Order> findByStatus(OrderStatus status);

    List<Order> findByStatusOrderByOrderTimeDesc(OrderStatus status);

    // Paginated variant — the orders table grows without bound (every order,
    // forever), unlike vendors/categories/promotions which are small and
    // admin-curated, so this is the one admin list view where fetching the
    // full table client-side is a real scale risk rather than a theoretical one.
    Page<Order> findByStatusOrderByOrderTimeDesc(OrderStatus status, Pageable pageable);

    /**
     * One grouped query returning a count per {@link OrderStatus} value present
     * in the table — powers the admin Orders dashboard's stat cards without the
     * frontend having to bucket a client-fetched list (which, combined with the
     * status-filtered fetch used for the table itself, previously made every
     * card except the active tab's silently read 0 — see AdminOrdersPage).
     */
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countGroupedByStatus();

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
    // NOTE: these take explicit start/end-of-day bounds rather than using
    // DATE(o.orderTime) = CURRENT_DATE — Hibernate 7's HQL type-checker can't
    // infer a comparable type for DATE() applied to a LocalDateTime attribute
    // ("Cannot compare left expression of type 'java.lang.Object' with right
    // expression of type 'java.sql.Date'"), which fails repository bean
    // creation at startup. A plain range comparison sidesteps the inference
    // bug entirely and is portable across MySQL/H2.
    @Query("SELECT o FROM Order o WHERE o.orderTime >= :startOfDay AND o.orderTime < :endOfDay ORDER BY o.orderTime DESC")
    List<Order> findTodayOrders(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT o FROM Order o WHERE o.vendor = :vendor AND o.orderTime >= :startOfDay AND o.orderTime < :endOfDay ORDER BY o.orderTime DESC")
    List<Order> findTodayOrdersByVendor(@Param("vendor") VendorProfile vendor,
                                         @Param("startOfDay") LocalDateTime startOfDay,
                                         @Param("endOfDay") LocalDateTime endOfDay);

    // Revenue calculations
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'DELIVERED'")
    BigDecimal calculateTotalRevenue();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.vendor = :vendor AND o.status = 'DELIVERED'")
    BigDecimal calculateVendorRevenue(@Param("vendor") VendorProfile vendor);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.vendor = :vendor AND o.status = 'DELIVERED' AND o.orderTime >= :startOfDay AND o.orderTime < :endOfDay")
    BigDecimal calculateVendorTodayRevenue(@Param("vendor") VendorProfile vendor,
                                            @Param("startOfDay") LocalDateTime startOfDay,
                                            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.vendor = :vendor AND o.status = 'DELIVERED' AND o.orderTime >= :startDate")
    BigDecimal calculateVendorRevenueFromDate(@Param("vendor") VendorProfile vendor, @Param("startDate") LocalDateTime startDate);

    // Count queries
    Long countByCustomer(CustomerProfile customer);

    Long countByVendor(VendorProfile vendor);

    Long countByStatus(OrderStatus status);

    Long countByVendorAndStatus(VendorProfile vendor, OrderStatus status);

    /**
     * Batched version of {@link #countByCustomer(CustomerProfile)} — one grouped
     * query for a whole page of customer IDs instead of one COUNT per row.
     * Each result row is {@code [customerProfileId, count]}. Callers should
     * default missing IDs (customers with zero orders) to 0 themselves, since
     * a GROUP BY simply omits rows with no matches.
     */
    @Query("SELECT o.customer.customerProfileId, COUNT(o) FROM Order o " +
           "WHERE o.customer.customerProfileId IN :customerProfileIds " +
           "GROUP BY o.customer.customerProfileId")
    List<Object[]> countGroupedByCustomerIds(@Param("customerProfileIds") List<Long> customerProfileIds);

    /**
     * Batched version of {@link #countByVendorAndStatus(VendorProfile, OrderStatus)}
     * — one grouped query for a whole page of vendor IDs instead of one COUNT
     * per row. Each result row is {@code [vendorProfileId, count]}.
     */
    @Query("SELECT o.vendor.id, COUNT(o) FROM Order o " +
           "WHERE o.vendor.id IN :vendorProfileIds AND o.status = :status " +
           "GROUP BY o.vendor.id")
    List<Object[]> countGroupedByVendorIdsAndStatus(@Param("vendorProfileIds") List<Long> vendorProfileIds,
                                                      @Param("status") OrderStatus status);

    Long countByVendorId(Long vendorId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderTime >= :startOfDay AND o.orderTime < :endOfDay")
    Long countTodayOrders(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.vendor = :vendor AND o.orderTime >= :startOfDay AND o.orderTime < :endOfDay")
    Long countVendorTodayOrders(@Param("vendor") VendorProfile vendor,
                                 @Param("startOfDay") LocalDateTime startOfDay,
                                 @Param("endOfDay") LocalDateTime endOfDay);

    // SLA — find PENDING orders whose accept window has expired
    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING' AND o.orderTime < :cutoff")
    List<Order> findExpiredPendingOrders(@Param("cutoff") LocalDateTime cutoff);

    // Safety Net — find orders still out-for-delivery or ready past the cutoff time.
    // Uses fulfillmentDeadline (set for every order at accept time — see
    // OrderService#computeFulfillmentDeadline), not requestedFulfillmentTime, so this
    // also catches SAME_DAY orders. requestedFulfillmentTime is only ever populated
    // for ADVANCE_ORDER items, so filtering on it silently excluded every same-day
    // order from this safety net.
    @Query("SELECT o FROM Order o WHERE o.status IN ('OUT_FOR_DELIVERY', 'READY_FOR_PICKUP') " +
           "AND o.fulfillmentDeadline IS NOT NULL AND o.fulfillmentDeadline < :cutoff")
    List<Order> findOverdueActiveOrders(@Param("cutoff") LocalDateTime cutoff);

    // Safety Net — find orders marked DELIVERED but whose payment was never captured
    @Query("SELECT o FROM Order o JOIN o.payment p " +
           "WHERE o.status = 'DELIVERED' AND p.status = 'AUTHORIZED'")
    List<Order> findDeliveredWithUnCapturedPayment();

    // Fulfillment overdue — CONFIRMED/PREPARING orders past their fulfillmentDeadline
    // that haven't been flagged yet
    @Query("SELECT o FROM Order o WHERE o.status IN ('CONFIRMED', 'PREPARING') " +
           "AND o.fulfillmentDeadline IS NOT NULL AND o.fulfillmentDeadline < :cutoff " +
           "AND o.overdueFlaggedAt IS NULL")
    List<Order> findNewlyOverdueOrders(@Param("cutoff") LocalDateTime cutoff);

    // Fulfillment overdue — CONFIRMED/PREPARING orders already flagged but still
    // unresolved past the second (auto-cancel) grace period
    @Query("SELECT o FROM Order o WHERE o.status IN ('CONFIRMED', 'PREPARING') " +
           "AND o.overdueFlaggedAt IS NOT NULL AND o.overdueFlaggedAt < :cutoff")
    List<Order> findUnresolvedFlaggedOrders(@Param("cutoff") LocalDateTime cutoff);

    // Count queries — by customer and status
    Long countByCustomerAndStatus(CustomerProfile customer, OrderStatus status);

    // Efficient count of active (non-terminal) orders
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status NOT IN ('DELIVERED', 'CANCELLED', 'REFUNDED')")
    Long countActiveOrders();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.vendor = :vendor AND o.status NOT IN ('DELIVERED', 'CANCELLED', 'REFUNDED')")
    Long countActiveOrdersByVendor(@Param("vendor") VendorProfile vendor);

    // Customer revenue (DELIVERED orders only)
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.customer = :customer AND o.status = 'DELIVERED'")
    BigDecimal calculateCustomerRevenue(@Param("customer") CustomerProfile customer);

    // Date-range aggregate queries (used by PlatformTrends — avoid loading all orders into memory)
    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderTime >= :startDate AND o.orderTime <= :endDate")
    Long countOrdersBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'DELIVERED' AND o.orderTime >= :startDate AND o.orderTime <= :endDate")
    BigDecimal calculateRevenueBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Date-range status count (used by AdminAnalytics date filter)
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.orderTime >= :startDate AND o.orderTime <= :endDate")
    Long countByStatusAndOrderTimeBetween(@Param("status") OrderStatus status,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    // Date-range active orders count
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status NOT IN ('DELIVERED', 'CANCELLED', 'REFUNDED') AND o.orderTime >= :startDate AND o.orderTime <= :endDate")
    Long countActiveOrdersBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

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
