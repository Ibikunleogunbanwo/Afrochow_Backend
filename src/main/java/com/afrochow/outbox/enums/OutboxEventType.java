package com.afrochow.outbox.enums;

/**
 * All domain events that flow through the transactional outbox.
 * Most values map to notification work; command-style events are consumed by
 * dedicated workers such as payment transfer and geocoding consumers.
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
    PAYMENT_TRANSFER_REQUESTED,  // → PaymentTransferEventConsumer

    // ── Engagement ───────────────────────────────────────────────────────────
    VENDOR_REVIEWED,            // → notifyVendorNewReview
    VENDOR_FAVOURITED,          // → notifyVendorFavorited
    VENDOR_CUSTOMER_CANCELLED,  // → notifyVendorCustomerCancelled (customer cancelled a CONFIRMED order)
    VENDOR_UNABLE_TO_FULFIL,    // → notifyCustomerVendorUnableToFulfil (vendor cancelled after accepting — payment already captured, refund issued)

    // ── Auth / account lifecycle ─────────────────────────────────────────────
    USER_REGISTERED,            // → notifyUserRegistered   (welcome email + in-app)
    PASSWORD_CHANGED,           // → notifyPasswordChanged  (security alert email + in-app)
    PASSWORD_RESET_REQUESTED,   // → notifyPasswordResetRequested (reset-link email + in-app)
    EMAIL_VERIFICATION_SENT,    // → notifyEmailVerificationSent (verification email only)
    ACCOUNT_DELETION_REQUESTED, // → notifyAccountDeletionRequested

    // ── Address lifecycle ───────────────────────────────────────────────────
    ADDRESS_GEOCODING_REQUESTED, // → AddressGeocodingEventConsumer

    // ── Vendor admin lifecycle ───────────────────────────────────────────────
    VENDOR_PROVISIONAL,         // → notifyVendorProvisional  (live, cert upload required)
    VENDOR_CERTIFICATE_UPLOADED,// → notifyAdminsVendorCertificateUploaded
    VENDOR_APPROVED,            // → notifyVendorApproved     (fully verified after cert check)
    VENDOR_REJECTED,            // → notifyVendorRejected
    VENDOR_SUSPENDED,           // → notifyVendorSuspended
    VENDOR_REINSTATED           // → notifyVendorReinstated
}
