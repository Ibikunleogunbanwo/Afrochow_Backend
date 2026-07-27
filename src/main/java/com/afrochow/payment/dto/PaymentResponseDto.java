package com.afrochow.payment.dto;
import com.afrochow.common.enums.PaymentMethod;
import com.afrochow.common.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDto {

    private String publicOrderId;
    private BigDecimal amount;
    private BigDecimal platformFeeAmount;
    private BigDecimal vendorPayout;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String transactionId;
    private String maskedCardNumber;
    private String cardBrand;
    private String notes;

    private Boolean isSuccessful;
    private Boolean isPending;
    private Boolean isFailed;
    private Boolean isRefunded;

    private LocalDateTime paymentTime;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    private LocalDateTime refundedAt;

    /**
     * True when Stripe needs the customer to complete a 3D Secure challenge before
     * this payment can be authorized. Only ever set by the endpoints that actually run
     * a Stripe charge attempt (order creation, retry, confirm) — null/false elsewhere.
     */
    private Boolean requiresAction;

    /**
     * The Stripe PaymentIntent client secret to pass to stripe.confirmCardPayment() on
     * the frontend. Only populated when requiresAction is true. Never persisted to the
     * database — this is ephemeral, sourced fresh from Stripe on each relevant call.
     */
    private String stripeClientSecret;
}