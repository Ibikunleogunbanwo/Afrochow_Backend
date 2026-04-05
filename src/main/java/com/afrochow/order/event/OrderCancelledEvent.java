package com.afrochow.order.event;

public record OrderCancelledEvent(String publicOrderId, String reason, String previousStatus) {}
