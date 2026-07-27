-- ===========================================================================
-- Afrochow Database Migration V16
-- Description: Ensure PaymentStatus.AUTHORIZED is present in the payment.status
--              ENUM column.
--
-- Context: V14 added AUTHORIZED to the ENUM, but it may not have run on all
-- production instances before the manual-capture payment flow went live.
-- This migration re-applies the full ENUM definition idempotently — MySQL
-- silently no-ops a MODIFY COLUMN when the column definition is unchanged.
-- ===========================================================================

ALTER TABLE payment
    MODIFY status ENUM(
        'PENDING',
        'AUTHORIZED',
        'COMPLETED',
        'FAILED',
        'REFUNDED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'PENDING';

-- ===========================================================================
-- End of V16 migration
-- ===========================================================================
