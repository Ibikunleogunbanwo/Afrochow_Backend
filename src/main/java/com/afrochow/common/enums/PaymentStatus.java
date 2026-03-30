package com.afrochow.common.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PENDING("Payment is being processed"),
    AUTHORIZED("Card authorised — hold placed, not yet captured"),
    COMPLETED("Payment successfully completed"),
    FAILED("Payment failed"),
    REFUNDED("Payment was refunded"),
    CANCELLED("Payment was cancelled");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

}