package com.afrochow.outbox.enums;

/**
 * All domain events that flow through the transactional outbox.
 * Each value maps 1-to-1 to a NotificationService method.
 */
public enum OutboxEventType {

    // ── Order lifecycle ──────────────────────────────────────────────────────
    ORDER_PLACED,               // → notifyVendorNewOrder
    CUSTOMER_ORDER_RECEIVED,    // → notifyCustomerOrderReceived
    ORDER_CONFIRMED,            // → notifyCustomerOrderConfirmed
    ORDER_CANCELLED,            // → notifyCustomerOrderCancelled
    ORDER_PREPARING,            // → notifyCustomerOrderPreparing
    ORDER_READY,                // → notifyCustomerOrderReady
    ORDER_OUT_FOR_DELIVERY,     // → notifyCustomerOrderOutForDelivery
    ORDER_DELIVERED,            // → notifyCustomerOrderDelivered

    // ── Payment ──────────────────────────────────────────────────────────────
    PAYMENT_CAPTURED,           // → notifyPaymentSuccess
    PAYMENT_FAILED,             // → notifyPaymentFailed

    // ── Engagement ───────────────────────────────────────────────────────────
    VENDOR_REVIEWED,            // → notifyVendorNewReview
    VENDOR_FAVOURITED           // → notifyVendorFavorited
}
