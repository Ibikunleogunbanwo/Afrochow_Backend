package com.afrochow.common.enums;

/**
 * Resource areas within the admin panel that {@code AdminProfile.department}
 * can be scoped to. Used exclusively by {@code com.afrochow.admin.security.DeptAccess}
 * to decide whether a plain ADMIN (not SUPERADMIN) may access a given admin
 * controller endpoint, based on their department.
 *
 * <p>SUPERADMIN accounts bypass this entirely and always have access to every
 * area, regardless of their own department field — see {@code DeptAccess.can()}.
 */
public enum AdminArea {
    /** User account management: view, deactivate, unlock. (Role changes and deletion are SUPERADMIN-only regardless of area.) */
    USERS,
    /** Vendor verification, suspension, reinstatement, rejection. (Stripe account relinking is SUPERADMIN-only regardless of area.) */
    VENDORS,
    /** Product moderation: view, hide/unhide. (Deletion is SUPERADMIN-only regardless of area.) */
    PRODUCTS,
    /** Order oversight and viewing. */
    ORDERS,
    /** Payment records and refunds. */
    PAYMENTS,
    /** Review moderation: hide/show/delete. */
    REVIEWS,
    /** Platform-wide promotion management. */
    PROMOTIONS,
    /** Sending admin broadcast notifications. */
    BROADCAST,
    /** Product category management. */
    CATEGORIES,
    /** Platform analytics and trend reports. */
    REPORTS
}
