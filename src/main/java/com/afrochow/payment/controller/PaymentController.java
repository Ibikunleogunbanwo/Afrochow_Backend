package com.afrochow.payment.controller;

import com.afrochow.common.response.ApiResponse;
import com.afrochow.payment.dto.PaymentResponseDto;
import com.afrochow.payment.dto.PaymentStatsDto;
import com.afrochow.payment.dto.RetryPaymentRequestDto;
import com.afrochow.security.model.CustomUserDetails;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for payment management
 *
 * Customer endpoints (requires CUSTOMER role):
 * - GET /customer/payments/order/{publicOrderId} - Get payment for order
 * - POST /customer/payments/order/{publicOrderId}/confirm - Confirm payment after a 3D Secure challenge
 * - POST /customer/payments/order/{publicOrderId}/retry - Retry a failed payment with a new card
 *
 * Admin endpoints (requires ADMIN/SUPERADMIN role):
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

    @GetMapping("/customer/payments/order/{publicOrderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get payment", description = "Get payment details for an order")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> getPayment(
            @PathVariable String publicOrderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        PaymentResponseDto payment = paymentService.getPaymentByOrderId(userId, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    @PostMapping("/customer/payments/order/{publicOrderId}/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Confirm payment after 3D Secure",
               description = "Call after stripe.confirmCardPayment() succeeds on the frontend, to finalize the order's payment")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> confirmPayment(
            @PathVariable String publicOrderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        PaymentResponseDto payment = paymentService.confirmPayment(userId, publicOrderId);
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed", payment));
    }

    @PostMapping("/customer/payments/order/{publicOrderId}/retry")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Retry payment", description = "Retry a failed payment with a new card")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> retryPayment(
            @PathVariable String publicOrderId,
            @Valid @RequestBody RetryPaymentRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        PaymentResponseDto payment = paymentService.retryPayment(userId, publicOrderId, request.getPaymentMethodId());
        return ResponseEntity.ok(ApiResponse.success("Payment retried", payment));
    }

    // ========== ADMIN ENDPOINTS ==========

    @GetMapping("/admin/payments")
    @PreAuthorize("@deptAccess.can('PAYMENTS')") // PAYMENTS area = FINANCE department (or SUPERADMIN)
    @Operation(summary = "Get all payments", description = "Get all payments in the system")
    public ResponseEntity<ApiResponse<List<PaymentResponseDto>>> getAllPayments() {
        List<PaymentResponseDto> payments = paymentService.getAllPayments();
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    @GetMapping("/admin/payments/stats")
    @PreAuthorize("@deptAccess.can('PAYMENTS')") // PAYMENTS area = FINANCE department (or SUPERADMIN)
    @Operation(summary = "Get payment stats", description = "Aggregate payment counts by status, for the dashboard stat cards")
    public ResponseEntity<ApiResponse<PaymentStatsDto>> getPaymentStats() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentStats()));
    }

    @GetMapping("/admin/payments/transaction/{transactionId}")
    @PreAuthorize("@deptAccess.can('PAYMENTS')") // PAYMENTS area = FINANCE department (or SUPERADMIN)
    @Operation(summary = "Get payment by transaction", description = "Get payment by transaction ID")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> getPaymentByTransaction(
            @PathVariable String transactionId
    ) {
        PaymentResponseDto payment = paymentService.getPaymentByTransactionId(transactionId);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    @GetMapping("/admin/payments/order/{publicOrderId}")
    @PreAuthorize("@deptAccess.can('PAYMENTS')") // PAYMENTS area = FINANCE department (or SUPERADMIN)
    @Operation(summary = "Get payment by order", description = "Get payment for a specific order")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> getPaymentByOrder(
            @PathVariable String publicOrderId
    ) {
        PaymentResponseDto payment = paymentService.getPaymentByOrderIdAdmin(publicOrderId);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    @GetMapping("/admin/payments/status/{status}")
    @PreAuthorize("@deptAccess.can('PAYMENTS')") // PAYMENTS area = FINANCE department (or SUPERADMIN)
    @Operation(summary = "Get payments by status", description = "Get payments filtered by status")
    public ResponseEntity<ApiResponse<List<PaymentResponseDto>>> getPaymentsByStatus(
            @PathVariable PaymentStatus status
    ) {
        List<PaymentResponseDto> payments = paymentService.getPaymentsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    @GetMapping("/admin/payments/failed")
    @PreAuthorize("@deptAccess.can('PAYMENTS')") // PAYMENTS area = FINANCE department (or SUPERADMIN)
    @Operation(summary = "Get failed payments", description = "Get all failed payments")
    public ResponseEntity<ApiResponse<List<PaymentResponseDto>>> getFailedPayments() {
        List<PaymentResponseDto> payments = paymentService.getFailedPayments();
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    @GetMapping("/admin/payments/stranded-payouts")
    @PreAuthorize("@deptAccess.can('PAYMENTS')") // PAYMENTS area = FINANCE department (or SUPERADMIN)
    @Operation(summary = "Get stranded payouts",
               description = "Captured payments whose vendor transfer never completed — money still held by the platform")
    public ResponseEntity<ApiResponse<List<PaymentResponseDto>>> getStrandedPayouts() {
        List<PaymentResponseDto> payments = paymentService.getStrandedPayouts();
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    @PostMapping("/admin/payments/order/{publicOrderId}/refund")
    @PreAuthorize("@deptAccess.can('PAYMENTS')") // PAYMENTS area = FINANCE department (or SUPERADMIN)
    @Operation(summary = "Refund payment", description = "Process a refund for a completed payment")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> refundPayment(
            @PathVariable String publicOrderId
    ) {
        PaymentResponseDto payment = paymentService.refundPayment(publicOrderId);
        return ResponseEntity.ok(ApiResponse.success("Payment refunded successfully", payment));
    }
}
