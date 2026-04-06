package com.afrochow.order.model;

import com.afrochow.address.model.Address;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.orderline.model.OrderLine;
import com.afrochow.payment.model.Payment;
import com.afrochow.promotion.model.PromotionUsage;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.vendor.model.VendorProfile;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_public_order_id", columnList = "publicOrderId"),
        @Index(name = "idx_customer_id",     columnList = "customer_profile_id"),
        @Index(name = "idx_vendor_id",       columnList = "vendor_profile_id"),
        @Index(name = "idx_status",          columnList = "status"),
        @Index(name = "idx_order_time",      columnList = "orderTime")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Column(unique = true, nullable = false)
    private String publicOrderId;

    // ========== FINANCIALS ==========

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    /**
     * Tax amount stored on the order.
     * Calculated server-side using ProvincialTax based on delivery/vendor province.
     * No longer uses a hardcoded 5% rate.
     */
    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;

    /**
     * The tax rate used when this order was placed (e.g. 0.13 for Ontario HST).
     * Stored so the rate is preserved even if provincial rates change later.
     */
    @Column(precision = 6, scale = 5)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    /** Human-readable tax label stored for receipts (e.g. "HST", "GST + PST"). */
    @Column(length = 20)
    private String taxLabel;

    /** Province code used for tax calculation (e.g. "ON", "AB"). */
    @Column(length = 2)
    private String taxProvince;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    /** Promo code applied to this order (nullable — no promo applied). */
    @Column(length = 50)
    private String appliedPromoCode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // ========== FULFILLMENT ==========

    /**
     * DELIVERY or PICKUP.
     * Nullable = false — every order must specify fulfillment type.
     */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String fulfillmentType = "DELIVERY";

    /**
     * Customer's requested fulfilment date/time — required when the cart contains
     * any ADVANCE_ORDER product. Null for same-day orders.
     */
    private LocalDateTime requestedFulfillmentTime;

    // ========== ORDER STATUS ==========

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(length = 500)
    private String specialInstructions;

    // ========== TIMESTAMPS ==========

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime orderTime;

    private LocalDateTime confirmedAt;
    private LocalDateTime preparingAt;
    private LocalDateTime readyAt;
    private LocalDateTime outForDeliveryAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ========== RELATIONSHIPS ==========

    @ManyToOne
    @JoinColumn(name = "customer_profile_id", nullable = false)
    private CustomerProfile customer;

    @ManyToOne
    @JoinColumn(name = "vendor_profile_id", nullable = false)
    private VendorProfile vendor;

    /**
     * Nullable — delivery orders have an address, pickup orders do not.
     * Changed from nullable = false to nullable = true.
     */
    @ManyToOne
    @JoinColumn(name = "delivery_address_id", nullable = true)
    private Address deliveryAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderLine> orderLines = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Payment payment;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private PromotionUsage promotionUsage;

    // ========== LIFECYCLE HOOKS ==========

    @PrePersist
    public void onPrePersist() {
        if (this.publicOrderId == null) {
            this.publicOrderId = "ORD-" + UUID.randomUUID().toString();
        }
        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }
        updateFinancials();
    }

    @PreUpdate
    public void onPreUpdate() {
        updateFinancials();
    }

    // ========== HELPER METHODS ==========

    public void addOrderLine(OrderLine line) {
        orderLines.add(line);
        line.setOrder(this);
    }

    @Transient
    public BigDecimal calculateSubtotal() {
        return orderLines.stream()
                .map(OrderLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate tax using the stored taxRate field.
     * Called by updateFinancials() — taxRate must be set before @PrePersist fires.
     */
    @Transient
    public BigDecimal calculateTax() {
        BigDecimal rate = this.taxRate != null ? this.taxRate : BigDecimal.ZERO;
        BigDecimal sub  = calculateSubtotal();
        BigDecimal fee  = deliveryFee != null ? deliveryFee : BigDecimal.ZERO;
        // Tax applies to subtotal + delivery fee
        return sub.add(fee).multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    @Transient
    public BigDecimal calculateTotal() {
        BigDecimal sub      = calculateSubtotal();
        BigDecimal delivery = deliveryFee != null ? deliveryFee : BigDecimal.ZERO;
        BigDecimal taxAmt   = calculateTax();
        BigDecimal disc     = discount   != null ? discount    : BigDecimal.ZERO;
        return sub.add(delivery).add(taxAmt).subtract(disc);
    }

    private void updateFinancials() {
        this.subtotal    = calculateSubtotal();
        this.tax         = calculateTax();
        this.totalAmount = calculateTotal();
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        LocalDateTime now = LocalDateTime.now();
        switch (newStatus) {
            case CONFIRMED       -> this.confirmedAt       = now;
            case PREPARING       -> this.preparingAt       = now;
            case READY_FOR_PICKUP-> this.readyAt           = now;
            case OUT_FOR_DELIVERY-> this.outForDeliveryAt  = now;
            case DELIVERED       -> this.deliveredAt       = now;
            case CANCELLED       -> this.cancelledAt       = now;
        }
    }

    /**
     * Returns true if the customer may still cancel this order.
     *
     * Rules:
     *  1. Status must be PENDING or CONFIRMED — once the kitchen starts (PREPARING+)
     *     cancellation is no longer possible regardless of timing.
     *  2. The order must have been placed within {@code windowHours} hours ago.
     *     This protects vendors on advance orders — a customer cannot cancel a
     *     catering order the day before the event just because it is still CONFIRMED.
     *
     * @param windowHours  configured via order.cancellation.window-hours
     */
    @Transient
    public boolean canBeCancelled(int windowHours) {
        if (status != OrderStatus.PENDING && status != OrderStatus.CONFIRMED) return false;
        if (orderTime == null) return true; // safety: no placement time recorded, allow
        return LocalDateTime.now().isBefore(orderTime.plusHours(windowHours));
    }

    @Transient
    public boolean isCompleted() {
        return status == OrderStatus.DELIVERED;
    }

    @Transient
    public boolean isActive() {
        return status != OrderStatus.DELIVERED  &&
                status != OrderStatus.CANCELLED  &&
                status != OrderStatus.REFUNDED;
    }

    @Transient
    public LocalDateTime getEstimatedDeliveryTime() {
        if (vendor == null || orderTime == null) return null;
        return orderTime.plusMinutes(vendor.getEstimatedDeliveryMinutes());
    }
}