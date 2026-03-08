package com.afrochow.payment.model;

import com.afrochow.common.enums.PaymentMethod;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.order.model.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_transaction_id", columnList = "transactionId"),
        @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @OneToOne
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(length = 100)
    private String transactionId;

    @Column(length = 4)
    private String cardLast4;

    @Column(length = 20)
    private String cardBrand;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime paymentTime;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    private LocalDateTime refundedAt;

    @Transient
    public Map<String, Boolean> getStatusFlags() {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("isSuccessful", status == PaymentStatus.COMPLETED);
        flags.put("isPending", status == PaymentStatus.PENDING);
        flags.put("isFailed", status == PaymentStatus.FAILED);
        flags.put("isRefunded", status == PaymentStatus.REFUNDED);
        return flags;
    }

    @Transient
    public boolean isSuccessful() {
        return status == PaymentStatus.COMPLETED;
    }

    @Transient
    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    @Transient
    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }

    @Transient
    public boolean isRefunded() {
        return status == PaymentStatus.REFUNDED;
    }

    @Transient
    public String getMaskedCardNumber() {
        return (cardLast4 != null) ? "**** **** **** " + cardLast4 : null;
    }

    // ===== Unified helper to update status, timestamps, and card info =====
    public void completePayment(String gatewayCardLast4, String gatewayCardBrand) {
        this.status = PaymentStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        setCardInfoSafely(gatewayCardLast4, gatewayCardBrand);
    }

    public void failPayment() {
        this.status = PaymentStatus.FAILED;
        this.failedAt = LocalDateTime.now();
    }

    public void refundPayment() {
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
    }

    private void setCardInfoSafely(String last4, String brand) {
        if (last4 != null && last4.matches("\\d{4}")) {
            this.cardLast4 = last4;
        }
        if (brand != null && !brand.isBlank()) {
            this.cardBrand = brand;
        }
    }

    public String publicOrderId() {
        return (order != null) ? order.getPublicOrderId() : null;
    }

}
