package com.afrochow.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /customer/payments/order/{publicOrderId}/retry.
 * A retry always needs a fresh Stripe payment method — reusing the original one
 * would just fail the same way again.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetryPaymentRequestDto {
    private String paymentMethodId;
}
