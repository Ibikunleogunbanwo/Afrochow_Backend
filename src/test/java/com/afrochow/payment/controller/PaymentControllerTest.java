package com.afrochow.payment.controller;

import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.payment.dto.PaymentResponseDto;
import com.afrochow.payment.dto.PaymentStatsDto;
import com.afrochow.payment.dto.RetryPaymentRequestDto;
import com.afrochow.payment.service.PaymentService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import com.afrochow.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for PaymentController.
 *
 * Customer endpoints take {@code @AuthenticationPrincipal CustomUserDetails}
 * and read {@code getUserId()} — authenticatedAsPrincipal with a User that
 * has userId populated. Admin endpoints take no auth param; class-level
 * {@code @PreAuthorize} rules (hasRole('CUSTOMER') / @deptAccess.can('PAYMENTS'))
 * are not exercised in this slice (see ControllerSliceTest javadoc).
 */
@ControllerSliceTest(PaymentController.class)
class PaymentControllerTest extends AbstractControllerTest {

    @MockitoBean private PaymentService paymentService;

    private static final Long USER_ID = 1L;

    private User customerUser() {
        return User.builder().userId(USER_ID).publicUserId("cust-1").build();
    }

    private PaymentResponseDto samplePayment(PaymentStatus status) {
        return PaymentResponseDto.builder()
                .publicOrderId("order-1")
                .amount(new BigDecimal("25.50"))
                .status(status)
                .transactionId("txn-1")
                .build();
    }

    @Test
    void getPayment_returns200() throws Exception {
        when(paymentService.getPaymentByOrderId(USER_ID, "order-1")).thenReturn(samplePayment(PaymentStatus.COMPLETED));

        mockMvc.perform(get("/customer/payments/order/{publicOrderId}", "order-1")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicOrderId").value("order-1"));
    }

    @Test
    void confirmPayment_returns200() throws Exception {
        when(paymentService.confirmPayment(USER_ID, "order-1")).thenReturn(samplePayment(PaymentStatus.COMPLETED));

        mockMvc.perform(post("/customer/payments/order/{publicOrderId}/confirm", "order-1")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void retryPayment_returns200() throws Exception {
        when(paymentService.retryPayment(USER_ID, "order-1", "pm_new123"))
                .thenReturn(samplePayment(PaymentStatus.PENDING));

        RetryPaymentRequestDto request = new RetryPaymentRequestDto();
        request.setPaymentMethodId("pm_new123");

        mockMvc.perform(post("/customer/payments/order/{publicOrderId}/retry", "order-1")
                        .with(authenticatedAsPrincipal(customerUser(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getAllPayments_returns200() throws Exception {
        when(paymentService.getAllPayments()).thenReturn(List.of(samplePayment(PaymentStatus.COMPLETED)));

        mockMvc.perform(get("/admin/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getPaymentStats_returns200() throws Exception {
        PaymentStatsDto stats = PaymentStatsDto.builder()
                .total(100).pending(10).authorized(5).completed(70).failed(8).refunded(5).cancelled(2)
                .build();
        when(paymentService.getPaymentStats()).thenReturn(stats);

        mockMvc.perform(get("/admin/payments/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(100))
                .andExpect(jsonPath("$.data.cancelled").value(2));
    }

    @Test
    void getPaymentByTransaction_returns200() throws Exception {
        when(paymentService.getPaymentByTransactionId("txn-1")).thenReturn(samplePayment(PaymentStatus.COMPLETED));

        mockMvc.perform(get("/admin/payments/transaction/{transactionId}", "txn-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value("txn-1"));
    }

    @Test
    void getPaymentByOrder_returns200() throws Exception {
        when(paymentService.getPaymentByOrderIdAdmin("order-1")).thenReturn(samplePayment(PaymentStatus.COMPLETED));

        mockMvc.perform(get("/admin/payments/order/{publicOrderId}", "order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicOrderId").value("order-1"));
    }

    @Test
    void getPaymentsByStatus_returns200() throws Exception {
        when(paymentService.getPaymentsByStatus(PaymentStatus.FAILED))
                .thenReturn(List.of(samplePayment(PaymentStatus.FAILED)));

        mockMvc.perform(get("/admin/payments/status/{status}", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getPaymentsByStatus_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/admin/payments/status/{status}", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFailedPayments_returns200() throws Exception {
        when(paymentService.getFailedPayments()).thenReturn(List.of(samplePayment(PaymentStatus.FAILED)));

        mockMvc.perform(get("/admin/payments/failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void refundPayment_returns200() throws Exception {
        when(paymentService.refundPayment("order-1")).thenReturn(samplePayment(PaymentStatus.REFUNDED));

        mockMvc.perform(post("/admin/payments/order/{publicOrderId}/refund", "order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }
}
