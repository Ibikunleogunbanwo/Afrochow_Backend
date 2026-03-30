package com.afrochow.payment.service;

import com.afrochow.common.enums.NotificationType;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.common.enums.RelatedEntityType;
import com.afrochow.notification.service.NotificationService;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.payment.dto.PaymentResponseDto;
import com.afrochow.payment.model.Payment;
import com.afrochow.payment.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing payments.
 * Handles both real Stripe charges (chargeOrder, refundStripeCharge)
 * and internal payment record management.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    @Value("${stripe.platform.fee-percent:10}")
    private int platformFeePercent;

    @Value("${stripe.connect.required:true}")
    private boolean connectRequired;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            NotificationService notificationService
    ) {
        this.paymentRepository   = paymentRepository;
        this.orderRepository     = orderRepository;
        this.notificationService = notificationService;
    }

    // ========== STRIPE METHODS ==========

    /**
     * Authorise (but do NOT capture) the customer's card via Stripe.
     * Uses capture_method=manual so money is only held, not moved.
     * The actual capture happens in captureStripePayment() when the vendor accepts the order.
     * If the vendor rejects or the customer cancels while still PENDING, the hold is released
     * by cancelling the PaymentIntent — no refund needed.
     *
     * On success : updates Payment record to AUTHORIZED.
     * On failure : updates Payment record to FAILED and throws RuntimeException.
     *
     * @param order           saved Order with totalAmount already calculated
     * @param paymentMethodId Stripe payment method token from frontend
     */
    @Transactional
    public void chargeOrder(Order order, String paymentMethodId) {
        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment record not found for order: " + order.getPublicOrderId()));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not in pending status");
        }

        String vendorStripeAccountId = order.getVendor().getStripeAccountId();
        boolean useConnect = connectRequired &&
                vendorStripeAccountId != null &&
                !vendorStripeAccountId.isBlank();
        if (connectRequired && !useConnect) {
            throw new IllegalStateException(
                    "Vendor does not have a Stripe account configured for payouts");
        }

        try {
            // Stripe works in the smallest currency unit — convert dollars to cents
            long amountInCents = order.getTotalAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            // Platform fee: e.g. 10% of total → kept by Afrochow; remainder goes to vendor
            long feeInCents = BigDecimal.valueOf(amountInCents)
                    .multiply(BigDecimal.valueOf(platformFeePercent))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("cad")
                    .setPaymentMethod(paymentMethodId)
                    .setConfirm(true)
                    // Manual capture: authorise the card now, capture when vendor accepts
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .setDescription("Afrochow order " + order.getPublicOrderId())
                    .putMetadata("publicOrderId",    order.getPublicOrderId())
                    .putMetadata("vendorPublicId",   order.getVendor().getPublicVendorId())
                    .putMetadata("customerPublicId", order.getCustomer().getUser().getPublicUserId())
                    .setReturnUrl("https://afrochow.ca/order-confirmation/" + order.getPublicOrderId());

            if (useConnect) {
                paramsBuilder
                        .setApplicationFeeAmount(feeInCents)
                        .setTransferData(
                                PaymentIntentCreateParams.TransferData.builder()
                                        .setDestination(vendorStripeAccountId)
                                        .build()
                        );
            }

            PaymentIntent intent = PaymentIntent.create(paramsBuilder.build());

            if ("requires_capture".equals(intent.getStatus())) {
                // Authorization succeeded — card hold placed, money not yet moved.
                // Capture will happen in captureStripePayment() when vendor accepts.
                String last4 = null;
                String brand = null;
                try {
                    com.stripe.model.PaymentMethod stripeMethod =
                            com.stripe.model.PaymentMethod.retrieve(paymentMethodId);
                    if (stripeMethod.getCard() != null) {
                        last4 = stripeMethod.getCard().getLast4();
                        brand = stripeMethod.getCard().getBrand();
                    }
                } catch (StripeException ignored) {
                    // Don't fail the order over cosmetic card details
                }

                payment.authorizePayment(last4, brand);
                payment.setTransactionId(intent.getId());
                payment.setPlatformFeeAmount(
                        BigDecimal.valueOf(feeInCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                payment.setVendorPayout(
                        BigDecimal.valueOf(amountInCents - feeInCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                paymentRepository.save(payment);

            } else if ("requires_action".equals(intent.getStatus())) {
                // 3D Secure required — client secret is surfaced to the frontend
                payment.setStatus(PaymentStatus.PENDING);
                payment.setTransactionId(intent.getId());
                payment.setNotes("Requires 3D Secure authentication");
                paymentRepository.save(payment);
                throw new RuntimeException("3DS_REQUIRED:" + intent.getClientSecret());

            } else {
                throw new RuntimeException(
                        "Stripe payment failed with status: " + intent.getStatus());
            }

        } catch (StripeException e) {
            payment.failPayment();
            payment.setNotes("Stripe error: " + e.getMessage());
            paymentRepository.save(payment);

            notificationService.notifyPaymentFailed(
                    order.getCustomer().getUser().getPublicUserId(),
                    order.getPublicOrderId(),
                    e.getMessage()
            );

            throw new RuntimeException("Payment failed: " + e.getMessage());
        }
    }

    /**
     * Capture a previously-authorised PaymentIntent.
     * Called by OrderService.acceptOrder() when the vendor accepts the order.
     * This is the moment money actually moves from the customer to Afrochow/vendor.
     *
     * On success : updates Payment record to COMPLETED.
     * On failure : throws RuntimeException — acceptOrder is @Transactional so the
     *              status update to CONFIRMED rolls back, keeping the order PENDING.
     *
     * @param order the order whose payment is to be captured
     */
    @Transactional
    public void captureStripePayment(Order order) {
        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment record not found for order: " + order.getPublicOrderId()));

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Cannot capture — payment is not in AUTHORIZED state (current: " + payment.getStatus() + ")");
        }

        if (payment.getTransactionId() == null) {
            throw new IllegalStateException(
                    "Cannot capture — no Stripe PaymentIntent ID on record");
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(payment.getTransactionId());
            PaymentIntent captured = intent.capture(PaymentIntentCaptureParams.builder().build());

            if ("succeeded".equals(captured.getStatus())) {
                payment.completePayment(payment.getCardLast4(), payment.getCardBrand());
                paymentRepository.save(payment);

                notificationService.notifyPaymentSuccess(
                        order.getCustomer().getUser().getPublicUserId(),
                        captured.getId(),
                        order.getPublicOrderId(),
                        order.getTotalAmount()
                );
            } else {
                throw new RuntimeException(
                        "Stripe capture returned unexpected status: " + captured.getStatus());
            }

        } catch (StripeException e) {
            throw new RuntimeException("Payment capture failed: " + e.getMessage());
        }
    }

    /**
     * Refund a completed Stripe charge, or cancel the hold if the payment was
     * only authorised and not yet captured.
     * Called by OrderService.cancelCustomerOrder() / rejectOrder() / adminCancelOrder().
     * Safe to call on unpaid/failed orders — returns silently.
     *
     * @param order the order to refund or cancel
     */
    @Transactional
    public void refundStripeCharge(Order order) {
        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment record not found for order: " + order.getPublicOrderId()));

        if (payment.getTransactionId() == null) {
            // No Stripe record — nothing to do
            return;
        }

        try {
            if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
                // ── Payment was never captured — cancel the hold instead of refunding ──
                // No money was moved, so no refund is needed. Cancelling the intent
                // releases the authorisation hold on the customer's card immediately.
                PaymentIntent intent = PaymentIntent.retrieve(payment.getTransactionId());
                intent.cancel();
                payment.cancelAuthorization();
                paymentRepository.save(payment);

            } else if (payment.getStatus() == PaymentStatus.COMPLETED) {
                // ── Payment was captured — issue a real Stripe refund ──
                boolean hasTransfer = order.getVendor().getStripeAccountId() != null
                        && !order.getVendor().getStripeAccountId().isBlank();
                com.stripe.param.RefundCreateParams.Builder refundBuilder =
                        com.stripe.param.RefundCreateParams.builder()
                                .setPaymentIntent(payment.getTransactionId());
                if (hasTransfer) {
                    // Connected account — reverse transfer and return platform fee
                    refundBuilder
                            .setRefundApplicationFee(true)
                            .setReverseTransfer(true);
                }
                com.stripe.model.Refund.create(refundBuilder.build());
                payment.refundPayment();
                paymentRepository.save(payment);

            }
            // All other statuses (PENDING, FAILED, CANCELLED, REFUNDED) — nothing to do

        } catch (StripeException e) {
            throw new RuntimeException("Stripe refund/cancel failed: " + e.getMessage());
        }
    }

    // ========== CUSTOMER METHODS ==========

    /**
     * Get payment for an order (customer)
     */
    public PaymentResponseDto getPaymentByOrderId(Long customerUserId, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException(
                    "You can only view payments for your own orders");
        }

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        return toResponseDto(payment);
    }

    /**
     * Process payment — legacy simulated method kept for backward compatibility.
     * New code should use chargeOrder() instead.
     */
    @Transactional
    public PaymentResponseDto processPayment(Long customerUserId, String publicOrderId,
                                             String cardLast4, String cardBrand) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException(
                    "You can only process payments for your own orders");
        }

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not in pending status");
        }

        try {
            payment.completePayment(cardLast4, cardBrand);
            payment.setTransactionId("TXN-" + System.currentTimeMillis());

            Payment savedPayment = paymentRepository.save(payment);

            notificationService.notifyPaymentSuccess(
                    order.getCustomer().getUser().getPublicUserId(),
                    savedPayment.getTransactionId(),
                    order.getPublicOrderId(),
                    savedPayment.getAmount()
            );

            return toResponseDto(savedPayment);

        } catch (Exception e) {
            payment.failPayment();
            paymentRepository.save(payment);

            notificationService.notifyPaymentFailed(
                    order.getCustomer().getUser().getPublicUserId(),
                    order.getPublicOrderId(),
                    e.getMessage()
            );

            throw new RuntimeException("Payment processing failed: " + e.getMessage());
        }
    }

    /**
     * Retry failed payment (customer)
     */
    @Transactional
    public PaymentResponseDto retryPayment(Long customerUserId, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException(
                    "You can only retry payments for your own orders");
        }

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException("Can only retry failed payments");
        }

        payment.setStatus(PaymentStatus.PENDING);
        Payment savedPayment = paymentRepository.save(payment);

        return toResponseDto(savedPayment);
    }

    // ========== ADMIN METHODS ==========

    /**
     * Get all payments (admin)
     */
    public List<PaymentResponseDto> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get payment by transaction ID (admin)
     */
    public PaymentResponseDto getPaymentByTransactionId(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found"));
        return toResponseDto(payment);
    }

    /**
     * Get payment by order public ID (admin)
     */
    public PaymentResponseDto getPaymentByOrderIdAdmin(String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        return toResponseDto(payment);
    }

    /**
     * Get payments by status (admin)
     */
    public List<PaymentResponseDto> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get failed payments (admin)
     */
    public List<PaymentResponseDto> getFailedPayments() {
        return paymentRepository.findFailedPayments().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Refund payment via admin — calls real Stripe refund.
     */
    @Transactional
    public PaymentResponseDto refundPayment(String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        refundStripeCharge(order);

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        notificationService.createNotification(
                order.getCustomer().getUser().getPublicUserId(),
                "Payment Refunded",
                String.format(
                        "Your payment of $%.2f for order #%s has been refunded. " +
                                "The refund will appear in your account within 5-10 business days.",
                        payment.getAmount(), order.getPublicOrderId()),
                NotificationType.PAYMENT_SUCCESS,
                RelatedEntityType.PAYMENT,
                payment.getTransactionId()
        );

        return toResponseDto(payment);
    }

    // ========== STATISTICS ==========

    public Long countPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.countByStatus(status);
    }

    // ========== MAPPING ==========

    private PaymentResponseDto toResponseDto(Payment payment) {
        return PaymentResponseDto.builder()
                .publicOrderId(payment.getOrder() != null
                        ? payment.getOrder().getPublicOrderId() : null)
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .maskedCardNumber(payment.getMaskedCardNumber())
                .cardBrand(payment.getCardBrand())
                .notes(payment.getNotes())
                .isSuccessful(payment.isSuccessful())
                .isPending(payment.isPending())
                .isFailed(payment.isFailed())
                .isRefunded(payment.isRefunded())
                .paymentTime(payment.getPaymentTime())
                .completedAt(payment.getCompletedAt())
                .failedAt(payment.getFailedAt())
                .refundedAt(payment.getRefundedAt())
                .build();
    }
}