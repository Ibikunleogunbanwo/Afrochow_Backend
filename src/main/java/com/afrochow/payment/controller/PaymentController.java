package com.afrochow.payment.controller;

import com.afrochow.payment.dto.PaymentResponseDto;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for payment management
 *
 * Customer endpoints (requires CUSTOMER role):
 * - GET /customer/payments/order/{publicOrderId} - Get payment for order
 * - POST /customer/payments/order/{publicOrderId}/process - Process payment
 * - POST /customer/payments/order/{publicOrderId}/retry - Retry failed payment
 *
 * Admin endpoints (requires ADMIN role with canManagePayments):
 * - GET /admin/payments - Get all payments
 * - GET /admin/payments/transaction/{transactionId} - Get payment by transaction ID
 * - GET /admin/payments/order/{publicOrderId} - Get payment by order ID
 * - GET /admin/payments/status/{status} - Get payments by status
 * - GET /admin/payments/failed - Get failed payments
 * - POST /admin/payments/order/{publicOrderId}/refund - Refund payment
 */
@RestController
@Tag(name = "Payments", description = "Payment management endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // ========== CUSTOMER ENDPOINTS ==========

    /**
     * Get payment for an order
     */
    @GetMapping("/customer/payments/order/{publicOrderId}")
    @Operation(summary = "Get payment", description = "Get payment details for an order")
    public ResponseEntity<PaymentResponseDto> getPayment(
            @PathVariable String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        PaymentResponseDto payment = paymentService.getPaymentByOrderId(userId, publicOrderId);
        return ResponseEntity.ok(payment);
    }

    /**
     * Process payment
     */
    @PostMapping("/customer/payments/order/{publicOrderId}/process")
    @Operation(summary = "Process payment", description = "Process payment for an order")
    public ResponseEntity<PaymentResponseDto> processPayment(
            @PathVariable String publicOrderId,
            @RequestParam(required = false) String cardLast4,
            @RequestParam(required = false) String cardBrand,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        PaymentResponseDto payment = paymentService.processPayment(userId, publicOrderId, cardLast4, cardBrand);
        return ResponseEntity.ok(payment);
    }

    /**
     * Retry failed payment
     */
    @PostMapping("/customer/payments/order/{publicOrderId}/retry")
    @Operation(summary = "Retry payment", description = "Retry a failed payment")
    public ResponseEntity<PaymentResponseDto> retryPayment(
            @PathVariable String publicOrderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        PaymentResponseDto payment = paymentService.retryPayment(userId, publicOrderId);
        return ResponseEntity.ok(payment);
    }

    // ========== ADMIN ENDPOINTS ==========

    /**
     * Get all payments
     */
    @GetMapping("/admin/payments")
    @Operation(summary = "Get all payments", description = "Get all payments in the system")
    public ResponseEntity<List<PaymentResponseDto>> getAllPayments() {
        List<PaymentResponseDto> payments = paymentService.getAllPayments();
        return ResponseEntity.ok(payments);
    }

    /**
     * Get payment by transaction ID
     */
    @GetMapping("/admin/payments/transaction/{transactionId}")
    @Operation(summary = "Get payment by transaction", description = "Get payment by transaction ID")
    public ResponseEntity<PaymentResponseDto> getPaymentByTransaction(
            @PathVariable String transactionId
    ) {
        PaymentResponseDto payment = paymentService.getPaymentByTransactionId(transactionId);
        return ResponseEntity.ok(payment);
    }

    /**
     * Get payment by order ID
     */
    @GetMapping("/admin/payments/order/{publicOrderId}")
    @Operation(summary = "Get payment by order", description = "Get payment for a specific order")
    public ResponseEntity<PaymentResponseDto> getPaymentByOrder(
            @PathVariable String publicOrderId
    ) {
        PaymentResponseDto payment = paymentService.getPaymentByOrderIdAdmin(publicOrderId);
        return ResponseEntity.ok(payment);
    }

    /**
     * Get payments by status
     */
    @GetMapping("/admin/payments/status/{status}")
    @Operation(summary = "Get payments by status", description = "Get payments filtered by status")
    public ResponseEntity<List<PaymentResponseDto>> getPaymentsByStatus(
            @PathVariable PaymentStatus status
    ) {
        List<PaymentResponseDto> payments = paymentService.getPaymentsByStatus(status);
        return ResponseEntity.ok(payments);
    }

    /**
     * Get failed payments
     */
    @GetMapping("/admin/payments/failed")
    @Operation(summary = "Get failed payments", description = "Get all failed payments")
    public ResponseEntity<List<PaymentResponseDto>> getFailedPayments() {
        List<PaymentResponseDto> payments = paymentService.getFailedPayments();
        return ResponseEntity.ok(payments);
    }

    /**
     * Refund payment
     */
    @PostMapping("/admin/payments/order/{publicOrderId}/refund")
    @Operation(summary = "Refund payment", description = "Process a refund for a completed payment")
    public ResponseEntity<PaymentResponseDto> refundPayment(
            @PathVariable String publicOrderId
    ) {
        PaymentResponseDto payment = paymentService.refundPayment(publicOrderId);
        return ResponseEntity.ok(payment);
    }
}
