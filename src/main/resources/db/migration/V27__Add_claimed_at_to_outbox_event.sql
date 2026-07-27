-- ===========================================================================
-- Guarded via information_schema checks rather than "ADD COLUMN IF NOT
-- EXISTS" / "CREATE INDEX IF NOT EXISTS" — that syntax produced a hard SQL
-- syntax error on this MySQL build. Guards are needed because several of
-- these columns already exist on dev DBs where Hibernate's ddl-auto=update
-- created them ahead of Flyway ever running — see
-- DotenvConfig.runFlywayMigrations() for the root-cause writeup.
-- ===========================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND COLUMN_NAME = 'claimed_at');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE outbox_event ADD COLUMN claimed_at DATETIME(6) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND COLUMN_NAME = 'event_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE outbox_event ADD COLUMN event_id VARCHAR(36) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND COLUMN_NAME = 'topic');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE outbox_event ADD COLUMN topic VARCHAR(120) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND COLUMN_NAME = 'aggregate_type');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE outbox_event ADD COLUMN aggregate_type VARCHAR(50) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND COLUMN_NAME = 'aggregate_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE outbox_event ADD COLUMN aggregate_id VARCHAR(100) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND COLUMN_NAME = 'published_at');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE outbox_event ADD COLUMN published_at DATETIME(6) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE outbox_event
SET event_id = UUID()
WHERE event_id IS NULL;

UPDATE outbox_event
SET aggregate_type = CASE
    WHEN event_type IN (
        'ORDER_PLACED',
        'CUSTOMER_ORDER_RECEIVED',
        'ORDER_CONFIRMED',
        'ORDER_CANCELLED',
        'ORDER_PREPARING',
        'ORDER_READY',
        'ORDER_OUT_FOR_DELIVERY',
        'ORDER_DELIVERED',
        'PAYMENT_FAILED',
        'VENDOR_CUSTOMER_CANCELLED',
        'VENDOR_UNABLE_TO_FULFIL'
    ) THEN 'ORDER'
    WHEN event_type = 'PAYMENT_CAPTURED' THEN 'PAYMENT'
    WHEN event_type IN ('VENDOR_REVIEWED', 'VENDOR_FAVOURITED') THEN 'VENDOR'
    ELSE 'USER'
END
WHERE aggregate_type IS NULL;

UPDATE outbox_event
SET aggregate_id = COALESCE(
    JSON_UNQUOTE(JSON_EXTRACT(payload, '$.publicOrderId')),
    JSON_UNQUOTE(JSON_EXTRACT(payload, '$.paymentId')),
    JSON_UNQUOTE(JSON_EXTRACT(payload, '$.vendorPublicId')),
    JSON_UNQUOTE(JSON_EXTRACT(payload, '$.publicUserId')),
    CONCAT('outbox-', id)
)
WHERE aggregate_id IS NULL;

UPDATE outbox_event
SET topic = 'afrochow.domain-events'
WHERE topic IS NULL;

ALTER TABLE outbox_event
    MODIFY COLUMN event_id VARCHAR(36) NOT NULL,
    MODIFY COLUMN topic VARCHAR(120) NOT NULL,
    MODIFY COLUMN aggregate_type VARCHAR(50) NOT NULL,
    MODIFY COLUMN aggregate_id VARCHAR(100) NOT NULL;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND INDEX_NAME = 'uk_outbox_event_event_id');
SET @sql := IF(@idx_exists = 0, 'ALTER TABLE outbox_event ADD UNIQUE INDEX uk_outbox_event_event_id (event_id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND INDEX_NAME = 'idx_outbox_status_created_at');
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_outbox_status_created_at ON outbox_event (status, created_at)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND INDEX_NAME = 'idx_outbox_status_claimed_at');
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_outbox_status_claimed_at ON outbox_event (status, claimed_at)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND INDEX_NAME = 'idx_outbox_topic_status_created_at');
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_outbox_topic_status_created_at ON outbox_event (topic, status, created_at)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_event' AND INDEX_NAME = 'idx_outbox_aggregate');
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_outbox_aggregate ON outbox_event (aggregate_type, aggregate_id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
