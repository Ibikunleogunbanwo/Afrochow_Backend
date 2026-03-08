package com.afrochow.payment.service;

import com.afrochow.payment.dto.PaymentResponseDto;
import com.afrochow.order.model.Order;
import com.afrochow.payment.model.Payment;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.notification.service.NotificationService;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.payment.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing payments
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            NotificationService notificationService
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
    }

    // ========== CUSTOMER METHODS ==========

    /**
     * Get payment for an order (customer)
     */
    public PaymentResponseDto getPaymentByOrderId(Long customerUserId, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        // Verify ownership
        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException("You can only view payments for your own orders");
        }

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for this order"));

        return toResponseDto(payment);
    }

    /**
     * Process payment (simulated - customer)
     * In a real application, this would integrate with a payment gateway
     */
    @Transactional
    public PaymentResponseDto processPayment(Long customerUserId, String publicOrderId, String cardLast4, String cardBrand) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        // Verify ownership
        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException("You can only process payments for your own orders");
        }

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for this order"));

        // Check if payment is pending
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not in pending status");
        }

        // Simulate payment processing
        // In production, this would call Stripe/PayPal/etc.
        try {
            // Simulate successful payment
            payment.completePayment(cardLast4, cardBrand);
            payment.setTransactionId("TXN-" + System.currentTimeMillis());

            Payment savedPayment = paymentRepository.save(payment);

            // Send payment success notifications (in-app + email)
            notificationService.notifyPaymentSuccess(
                    order.getCustomer().getUser().getPublicUserId(),
                    savedPayment.getTransactionId(),
                    order.getPublicOrderId(),
                    savedPayment.getAmount()
            );

            return toResponseDto(savedPayment);
        } catch (Exception e) {
            // Mark payment as failed
            payment.failPayment();
            paymentRepository.save(payment);

            // Send payment failed notifications (in-app + email)
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

        // Verify ownership
        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException("You can only retry payments for your own orders");
        }

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for this order"));

        // Check if payment failed
        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException("Can only retry failed payments");
        }

        // Reset to pending for retry
        payment.setStatus(PaymentStatus.PENDING);
        Payment savedPayment = paymentRepository.save(payment);

        return toResponseDto(savedPayment);
    }

    // ========== ADMIN METHODS ==========

    /**
     * Get all payments (admin)
     */
    public List<PaymentResponseDto> getAllPayments() {
        List<Payment> payments = paymentRepository.findAll();
        return payments.stream()
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
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for this order"));

        return toResponseDto(payment);
    }

    /**
     * Get payments by status (admin)
     */
    public List<PaymentResponseDto> getPaymentsByStatus(PaymentStatus status) {
        List<Payment> payments = paymentRepository.findByStatus(status);
        return payments.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get failed payments (admin)
     */
    public List<PaymentResponseDto> getFailedPayments() {
        List<Payment> payments = paymentRepository.findFailedPayments();
        return payments.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Refund payment (admin only)
     */
    @Transactional
    public PaymentResponseDto refundPayment(String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for this order"));

        // Check if payment is completed
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Can only refund completed payments");
        }

        // Process refund
        payment.refundPayment();
        Payment savedPayment = paymentRepository.save(payment);

        // Send refund notification (in-app only for now)
        // Note: Could be enhanced to use NotificationService.notifyPaymentRefund() if implemented
        notificationService.createNotification(
                order.getCustomer().getUser().getPublicUserId(),
                "Payment Refunded",
                String.format("Your payment of $%.2f for order #%s has been refunded. " +
                        "The refund will appear in your account within 5-10 business days.",
                        payment.getAmount(), order.getPublicOrderId()),
                com.afrochow.common.enums.NotificationType.PAYMENT_SUCCESS,
                com.afrochow.common.enums.RelatedEntityType.PAYMENT,
                savedPayment.getTransactionId()
        );

        return toResponseDto(savedPayment);
    }

    // ========== STATISTICS ==========

    /**
     * Count payments by status
     */
    public Long countPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.countByStatus(status);
    }

    // ========== MAPPING METHODS ==========

    private PaymentResponseDto toResponseDto(Payment payment) {
        return PaymentResponseDto.builder()
                .publicOrderId(payment.getOrder() != null ? payment.getOrder().getPublicOrderId() : null)
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
