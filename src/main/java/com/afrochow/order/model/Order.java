package com.afrochow.order.model;

import com.afrochow.address.model.Address;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.orderline.model.OrderLine;
import com.afrochow.payment.model.Payment;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_public_order_id", columnList = "publicOrderId"),
        @Index(name = "idx_customer_id", columnList = "customer_profile_id"),
        @Index(name = "idx_vendor_id", columnList = "vendor_profile_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_order_time", columnList = "orderTime")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.05"); // 5% tax

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

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

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

    @ManyToOne
    @JoinColumn(name = "delivery_address_id", nullable = false)
    private Address deliveryAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderLine> orderLines = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private Payment payment;

    // ========== AUTO-GENERATE PUBLIC ID ==========
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

    @Transient
    public BigDecimal calculateTax() {
        return calculateSubtotal().multiply(TAX_RATE);
    }

    @Transient
    public BigDecimal calculateTotal() {
        BigDecimal sub = calculateSubtotal();
        BigDecimal delivery = deliveryFee != null ? deliveryFee : BigDecimal.ZERO;
        BigDecimal taxAmount = calculateTax();
        BigDecimal disc = discount != null ? discount : BigDecimal.ZERO;
        return sub.add(delivery).add(taxAmount).subtract(disc);
    }

    // Updates subtotal, tax, and totalAmount fields
    private void updateFinancials() {
        this.subtotal = calculateSubtotal();
        this.tax = calculateTax();
        this.totalAmount = calculateTotal();
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        LocalDateTime now = LocalDateTime.now();
        switch (newStatus) {
            case CONFIRMED -> this.confirmedAt = now;
            case PREPARING -> this.preparingAt = now;
            case READY_FOR_PICKUP -> this.readyAt = now;
            case OUT_FOR_DELIVERY -> this.outForDeliveryAt = now;
            case DELIVERED -> this.deliveredAt = now;
            case CANCELLED -> this.cancelledAt = now;
        }
    }

    @Transient
    public boolean canBeCancelled() {
        return status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED;
    }

    @Transient
    public boolean isCompleted() {
        return status == OrderStatus.DELIVERED;
    }

    @Transient
    public boolean isActive() {
        return status != OrderStatus.DELIVERED &&
                status != OrderStatus.CANCELLED &&
                status != OrderStatus.REFUNDED;
    }

    @Transient
    public LocalDateTime getEstimatedDeliveryTime() {
        if (vendor == null) return null;
        return orderTime.plusMinutes(vendor.getEstimatedDeliveryMinutes());
    }
}
