package com.afrochow.common.exceptions;

/**
 * Thrown when someone tries to create a new CUSTOMER account (via the standard
 * registration form OR Google sign-in auto-provisioning) while the platform is
 * in customer-waitlist mode (app.customer-waitlist-mode=true).
 *
 * This is the backend enforcement of what was previously a frontend-only
 * convention (NEXT_PUBLIC_CUSTOMER_MODE / isCustomerWaitlistMode in mvp.js) —
 * that flag only hid/redirected the manual sign-up form. It did nothing to
 * stop a new customer account being silently created via "Sign in with
 * Google," which has no equivalent client-side gate. Vendor registration and
 * sign-in for EXISTING accounts of any role are unaffected.
 */
public class CustomerWaitlistModeException extends RuntimeException {

    public CustomerWaitlistModeException(String message) {
        super(message);
    }
}
