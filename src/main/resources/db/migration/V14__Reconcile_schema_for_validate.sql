-- ===========================================================================
-- Afrochow Database Migration V14
-- Description: Reconcile critical schema drift so Hibernate can run with ddl-auto=validate
-- Notes:
--  - This migration is intentionally additive / data-preserving.
--  - It aligns the DB with current JPA entities and enums used by the application.
-- ===========================================================================

-- ----------------------------
-- USERS: add SUPERADMIN role
-- ----------------------------
ALTER TABLE users
    MODIFY role ENUM('CUSTOMER', 'VENDOR', 'ADMIN', 'SUPERADMIN') NOT NULL DEFAULT 'CUSTOMER';

-- ----------------------------
-- ADDRESS: ensure column names expected by JPA exist
-- JPA expects: address_line, postal_code, province (enum stored as string)
-- Older migrations used: street
-- ----------------------------
SET @address_line_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'address'
      AND COLUMN_NAME = 'address_line'
);

SET @sql := IF(
    @address_line_exists = 0,
    'ALTER TABLE address ADD COLUMN address_line VARCHAR(200) NULL AFTER public_address_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Backfill address_line from street if present and address_line is null/blank
UPDATE address
   SET address_line = street
 WHERE (address_line IS NULL OR address_line = '')
   AND street IS NOT NULL
   AND street <> '';

-- ----------------------------
-- ORDERS: align status enum + allow pickup orders (nullable delivery_address_id)
-- ----------------------------
-- Map legacy enum values to current application values before tightening enum list.
UPDATE orders SET status = 'READY_FOR_PICKUP'  WHERE status = 'READY';
UPDATE orders SET status = 'OUT_FOR_DELIVERY'  WHERE status = 'DELIVERING';

ALTER TABLE orders
    MODIFY delivery_address_id BIGINT NULL,
    MODIFY status ENUM(
        'PENDING',
        'CONFIRMED',
        'PREPARING',
        'READY_FOR_PICKUP',
        'OUT_FOR_DELIVERY',
        'DELIVERED',
        'CANCELLED',
        'REFUNDED'
    ) NOT NULL DEFAULT 'PENDING';

-- ----------------------------
-- PAYMENT: align status enum to include AUTHORIZED (manual capture flow)
-- ----------------------------
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
-- End of V14 migration
-- ===========================================================================

