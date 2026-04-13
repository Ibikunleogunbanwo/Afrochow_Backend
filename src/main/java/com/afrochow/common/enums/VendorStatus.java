package com.afrochow.common.enums;

/**
 * Represents the lifecycle state of a vendor on the Afrochow platform.
 *
 * State machine transitions:
 *
 *   PENDING_PROFILE
 *       │  (vendor completes profile + address)
 *       ▼
 *   PENDING_REVIEW
 *       │  (admin approves provisionally — cert not yet uploaded)
 *       ▼
 *   PROVISIONAL ◄─── (cert upload pending; limited order cap applies)
 *       │  (food handling cert uploaded + admin marks cert verified)
 *       ▼
 *   VERIFIED
 *       │
 *       ├──► SUSPENDED   (admin suspends an active vendor)
 *       │       │  (admin reinstates)
 *       │       └──► VERIFIED
 *       │
 *       └──► REJECTED    (admin rejects application at any pre-verified stage)
 *               │  (vendor resubmits via /resubmit endpoint)
 *               └──► PENDING_REVIEW
 */
public enum VendorStatus {

    /**
     * Vendor has registered but has not yet completed their profile (restaurant info,
     * operating hours, address). Cannot receive orders.
     */
    PENDING_PROFILE,

    /**
     * Profile is complete and the vendor is awaiting admin review.
     * Cannot receive orders yet.
     */
    PENDING_REVIEW,

    /**
     * Vendor has been provisionally approved by an admin and can receive orders,
     * but has not yet uploaded or had their food handling certificate verified.
     * A daily order cap may be applied at this stage.
     */
    PROVISIONAL,

    /**
     * Vendor is fully verified — profile approved and food handling certificate confirmed.
     * Full platform access with no order caps.
     */
    VERIFIED,

    /**
     * Vendor has been suspended by an admin (e.g. compliance issue, complaint).
     * Cannot receive orders. Reversible via admin reinstate action.
     */
    SUSPENDED,

    /**
     * Vendor application was rejected. Cannot receive orders.
     * Vendor can resubmit, which moves them back to PENDING_REVIEW.
     */
    REJECTED
}
