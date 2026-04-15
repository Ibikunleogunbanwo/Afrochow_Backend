package com.afrochow.common.enums;

/**
 * Represents the lifecycle / account state of a user on the Afrochow platform.
 *
 * State machine:
 *
 *   PENDING_VERIFICATION
 *       │  (user clicks the OTP link in their inbox)
 *       ▼
 *   ACTIVE  ◄──────────────────── (admin reinstates)
 *       │
 *       ├──► SUSPENDED   (admin deactivates — isActive = false)
 *       │
 *       └──► LOCKED      (too many failed login attempts; auto-clears or admin unlocks)
 *
 * Resolution priority (highest → lowest):
 *   1. SUSPENDED  – isActive == false
 *   2. LOCKED     – account is locked by LoginAttemptService
 *   3. PENDING_VERIFICATION – emailVerified == false
 *   4. ACTIVE     – fully verified and in good standing
 */
public enum UserStatus {

    /**
     * User has registered but has not yet verified their email address.
     * Login is blocked until the OTP is confirmed.
     */
    PENDING_VERIFICATION,

    /**
     * Email verified, account active, no lockout — full platform access.
     */
    ACTIVE,

    /**
     * Account manually suspended by an admin (isActive = false).
     * Reversible via admin activate action.
     */
    SUSPENDED,

    /**
     * Account temporarily locked due to too many failed login attempts.
     * Auto-unlocks after the lockout window or via admin unlock action.
     */
    LOCKED
}
