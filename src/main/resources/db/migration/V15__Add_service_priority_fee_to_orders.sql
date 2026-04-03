-- ===========================================================================
-- Afrochow Database Migration V15
-- Description: Add serviceFee and priorityFee columns to orders table
--              and create ShedLock table for distributed scheduler locking
-- ===========================================================================

-- ----------------------------
-- ORDERS: add serviceFee and priorityFee columns
-- ----------------------------
ALTER TABLE orders
    ADD COLUMN service_fee   DECIMAL(10, 2) NOT NULL DEFAULT 0.00
        COMMENT 'Platform service fee charged to the customer'
        AFTER delivery_fee,
    ADD COLUMN priority_fee  DECIMAL(10, 2) NOT NULL DEFAULT 0.00
        COMMENT 'Optional priority/express fee chosen by the customer'
        AFTER service_fee;

-- Backfill existing rows: both fees default to 0.00 (safe, no data loss)
UPDATE orders SET service_fee = 0.00, priority_fee = 0.00
WHERE service_fee IS NULL OR priority_fee IS NULL;

-- ----------------------------
-- SHEDLOCK: distributed scheduler lock table
-- Required by ShedLock JDBC provider to prevent duplicate scheduler runs
-- across multiple application instances.
-- ----------------------------
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='ShedLock distributed scheduler coordination table';

-- ===========================================================================
-- End of V15 migration
-- ===========================================================================
