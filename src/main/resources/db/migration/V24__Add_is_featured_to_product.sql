-- ===========================================================================
-- Afrochow Database Migration V17
-- Description: Add is_featured flag to product table for manual admin curation
--
-- Guarded via information_schema checks rather than "ADD COLUMN IF NOT
-- EXISTS" — that syntax produced a hard SQL syntax error on this MySQL
-- build. Guards are needed because dev databases already had these columns
-- added ad hoc by Hibernate's ddl-auto=update, which was silently standing
-- in for Flyway — see DotenvConfig.runFlywayMigrations() for the writeup.
-- ===========================================================================

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'is_featured'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE product ADD COLUMN is_featured BOOLEAN NOT NULL DEFAULT FALSE COMMENT ''Admin-pinned featured product''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'featured_at'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE product ADD COLUMN featured_at DATETIME NULL COMMENT ''When admin last featured this product''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND INDEX_NAME = 'idx_is_featured'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_is_featured ON product (is_featured)',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- End of V17 migration
-- ===========================================================================
