-- ===========================================================================
-- Afrochow Database Migration V31
-- Description: Add fulfillment_deadline and overdue_flagged_at to orders, so
--              a scheduled safety net can catch CONFIRMED/PREPARING orders
--              the vendor never moves forward on.
--
-- fulfillment_deadline is computed once, when the vendor accepts the order
-- (see OrderService.acceptOrder):
--   - ADVANCE_ORDER items: the customer-chosen requestedFulfillmentTime.
--   - SAME_DAY items: confirmedAt + the longest preparationTimeMinutes
--     across the order's line items.
--
-- overdue_flagged_at is set the first time the deadline is found to have
-- passed (vendor + admin are notified at that point). If the order is still
-- unresolved a further grace period after being flagged, it is auto-cancelled
-- and refunded — see OrderFulfillmentOverdueScheduler.
--
-- Guarded via information_schema checks (not "ADD COLUMN IF NOT EXISTS") for
-- the same reason as V24-V29: that syntax produced a hard SQL syntax error on
-- this MySQL build, and dev's ddl-auto=update means these columns could also
-- already exist ad hoc by the time this migration runs.
-- ===========================================================================

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'fulfillment_deadline'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE orders ADD COLUMN fulfillment_deadline DATETIME NULL COMMENT ''When this order is expected to be ready/out for delivery, computed at accept time''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'overdue_flagged_at'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE orders ADD COLUMN overdue_flagged_at DATETIME NULL COMMENT ''When the fulfillment safety net first flagged this order as overdue''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_fulfillment_deadline'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_orders_fulfillment_deadline ON orders (status, fulfillment_deadline)',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_overdue_flagged_at'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_orders_overdue_flagged_at ON orders (status, overdue_flagged_at)',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- End of V31 migration
-- ===========================================================================
