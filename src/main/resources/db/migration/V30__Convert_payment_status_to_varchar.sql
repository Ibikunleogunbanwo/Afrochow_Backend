-- ===========================================================================
-- Afrochow Database Migration V30
-- Description: Convert payment.status from a hardcoded MySQL ENUM to VARCHAR
--              so future PaymentStatus.java additions never require a schema
--              migration to stay in sync again.
--
-- Context: V23 (mislabelled "V16" in its own header — a leftover from an
-- earlier renumbering) re-applied the full ENUM list including 'AUTHORIZED'
-- after checkout started failing with "Data truncated for column 'status'"
-- once the manual-capture/3D-Secure payment flow started writing that value.
-- The same class of failure resurfaced, because a native MySQL ENUM column
-- silently rejects (truncates) any value not in its fixed list, and nothing
-- guarantees the Java PaymentStatus enum and the DB ENUM list stay identical
-- over time. VARCHAR removes this entire class of bug going forward.
-- ===========================================================================

ALTER TABLE payment
    MODIFY status VARCHAR(30) NOT NULL DEFAULT 'PENDING';

-- ===========================================================================
-- End of V30 migration
-- ===========================================================================
