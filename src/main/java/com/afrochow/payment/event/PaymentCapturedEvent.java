package com.afrochow.payment.event;

import java.math.BigDecimal;

/**
 * Published after a payment is successfully captured and the capturing transaction commits.
 * Listeners use @TransactionalEventListener(phase = AFTER_COMMIT) so they only
 * fire once the payment record is visible to all DB readers.
 */
public record PaymentCapturedEvent(
        String userPublicId,
        String paymentId,
        String publicOrderId,
        BigDecimal amount
) {}
