package com.afrochow.payment.event;

public record PaymentFailedEvent(String userPublicId, String publicOrderId, String reason) {}
