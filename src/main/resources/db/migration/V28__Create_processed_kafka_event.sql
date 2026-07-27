-- CREATE TABLE IF NOT EXISTS is long-standing, universally-supported MySQL
-- syntax, so it's kept as-is. The two indexes below use the
-- information_schema-guarded dynamic SQL pattern instead of standalone
-- "CREATE INDEX IF NOT EXISTS", since that combined form produced a hard SQL
-- syntax error on this MySQL build elsewhere in this migration chain — see
-- DotenvConfig.runFlywayMigrations() for the root-cause writeup.
CREATE TABLE IF NOT EXISTS processed_kafka_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    consumer_name VARCHAR(120) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    outbox_id VARCHAR(40) NULL,
    event_type VARCHAR(64) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_processed_kafka_event_consumer_event UNIQUE (consumer_name, event_id)
);

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'processed_kafka_event' AND INDEX_NAME = 'idx_processed_kafka_event_event_id');
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_processed_kafka_event_event_id ON processed_kafka_event (event_id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'processed_kafka_event' AND INDEX_NAME = 'idx_processed_kafka_event_consumer_processed_at');
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_processed_kafka_event_consumer_processed_at ON processed_kafka_event (consumer_name, processed_at)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
