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
import java.util.UUID;

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

    /** Optimistic-locking version — prevents double-capture / double-refund races. */
    @Version
    private Long version;

    @Column(name = "public_payment_id", nullable = false, unique = true, length = 36)
    private String publicPaymentId;

    @OneToOne
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(precision = 10, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal vendorPayout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(length = 100)
    private String transactionId;

    /**
     * Stripe Transfer ID created when we pay out to the vendor at delivery.
     * Null until {@link com.afrochow.payment.service.PaymentService#transferToVendor} runs.
     * Presence of this field is the authoritative signal that a transfer exists and
     * must be reversed if a post-delivery refund is ever issued.
     */
    @Column(name = "stripe_transfer_id", length = 100)
    private String stripeTransferId;

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

    @PrePersist
    void ensurePublicPaymentId() {
        if (publicPaymentId == null || publicPaymentId.isBlank()) {
            publicPaymentId = UUID.randomUUID().toString();
        }
    }

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

    /**
     * Card authorised — hold placed on the customer's card.
     * Money has NOT moved yet. Call completePayment() when the intent is captured.
     */
    public void authorizePayment(String gatewayCardLast4, String gatewayCardBrand) {
        this.status = PaymentStatus.AUTHORIZED;
        setCardInfoSafely(gatewayCardLast4, gatewayCardBrand);
    }

    public void completePayment(String gatewayCardLast4, String gatewayCardBrand) {
        this.status = PaymentStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        setCardInfoSafely(gatewayCardLast4, gatewayCardBrand);
    }

    public void failPayment() {
        this.status = PaymentStatus.FAILED;
        this.failedAt = LocalDateTime.now();
    }

    /**
     * Authorisation cancelled before capture — the hold on the customer's
     * card is released. No money was ever moved.
     */
    public void cancelAuthorization() {
        this.status = PaymentStatus.CANCELLED;
        this.refundedAt = LocalDateTime.now(); // reuse timestamp field — records when hold was released
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
