package com.afrochow.common.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PENDING("Payment is being processed"),
    AUTHORIZED("Card authorised — hold placed, not yet captured"),
    COMPLETED("Payment successfully completed"),
    FAILED("Payment failed"),
    REFUNDED("Payment was refunded"),
    CANCELLED("Payment was cancelled"),
    /**
     * The customer raised a chargeback with their bank. Stripe has already debited
     * the amount from the platform balance pending the outcome. Terminal only if the
     * dispute is lost — a won dispute returns the payment to COMPLETED.
     */
    DISPUTED("Payment is disputed (chargeback)");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

}