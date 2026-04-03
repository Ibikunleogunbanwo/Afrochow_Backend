package com.afrochow.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Optional request body for the vendor "mark delivered" endpoint.
 *
 * When {@code finalAmount} is provided and differs from the original authorized
 * amount (e.g. because an item was substituted or removed), Stripe will capture
 * only the specified amount rather than the full authorization.
 *
 * If omitted entirely, the full authorized amount is captured — matching the
 * original order total.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkDeliveredRequestDto {

    /**
     * The actual amount to capture at delivery time.
     * Must be > 0 and must not exceed the originally authorized amount.
     * Leave null to capture the full authorized amount.
     */
    @DecimalMin(value = "0.01", message = "Final amount must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Final amount must have at most 2 decimal places")
    private BigDecimal finalAmount;
}
