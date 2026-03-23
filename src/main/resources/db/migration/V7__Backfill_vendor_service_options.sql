-- ===========================================================================
-- Afrochow Database Migration V7
-- Description: Ensure offers_pickup and offers_delivery are present and
--              backfill any NULL values to FALSE on existing vendor rows.
--              Columns may already exist (added by Hibernate DDL auto-update)
--              but could be nullable without a default on older rows.
-- Author: Afrochow Development Team
-- Date: 2026-03-23
-- ===========================================================================

-- Add columns if they were never created by Hibernate (safe no-op if present)
ALTER TABLE vendor_profile
    ADD COLUMN IF NOT EXISTS offers_delivery  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS offers_pickup    BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill any existing rows where Hibernate created the column as nullable
-- and no value was ever set
UPDATE vendor_profile
SET offers_delivery = FALSE
WHERE offers_delivery IS NULL;

UPDATE vendor_profile
SET offers_pickup = FALSE
WHERE offers_pickup IS NULL;

-- Enforce NOT NULL + default going forward (idempotent MODIFY)
ALTER TABLE vendor_profile
    MODIFY COLUMN offers_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    MODIFY COLUMN offers_pickup   BOOLEAN NOT NULL DEFAULT FALSE;

-- ===========================================================================
-- End of V7 migration
-- ===========================================================================
