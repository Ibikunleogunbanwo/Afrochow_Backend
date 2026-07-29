-- ===========================================================================
-- Afrochow Database Migration V38
-- Description: Add public_payment_id to the payment table, backing the new
--              Payment.publicPaymentId field (see payment-uuid-logs branch).
--              Mirrors the existing publicOrderId pattern on `order` — an
--              opaque, non-sequential identifier safe to expose in logs,
--              URLs, or API responses instead of the internal auto-increment
--              paymentId.
--
-- New rows get their value from Payment.ensurePublicPaymentId() (@PrePersist,
-- UUID.randomUUID()) at the application layer. Existing rows have no value,
-- so this migration backfills them at the database layer with MySQL's UUID()
-- function before the NOT NULL + UNIQUE constraints are applied.
--
-- Guarded via information_schema checks (same pattern as V25/V27/V35/V36) so
-- this is safe to run against environments where the column may already
-- exist from a prior manual/partial deploy.
-- ===========================================================================

-- Step 1: add the column as nullable first (can't add NOT NULL directly on a
-- table with existing rows and no default).
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment' AND COLUMN_NAME = 'public_payment_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE payment ADD COLUMN public_payment_id VARCHAR(36) NULL COMMENT ''Opaque public-safe payment identifier (UUID), distinct from internal paymentId''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Step 2: backfill any rows still missing a value (existing payments created
-- before this column existed).
UPDATE payment SET public_payment_id = UUID() WHERE public_payment_id IS NULL;

-- Step 3: enforce NOT NULL now that every row has a value. Guarded so this
-- is a no-op if already applied.
SET @col_nullable := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment' AND COLUMN_NAME = 'public_payment_id'
);
SET @sql := IF(@col_nullable = 'YES',
    'ALTER TABLE payment MODIFY COLUMN public_payment_id VARCHAR(36) NOT NULL',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Step 4: unique index, matching the @Column(unique = true) on the entity.
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment' AND INDEX_NAME = 'uk_public_payment_id'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE payment ADD CONSTRAINT uk_public_payment_id UNIQUE (public_payment_id)',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- End of V38 migration
-- ===========================================================================
