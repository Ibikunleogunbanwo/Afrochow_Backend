-- Guarded via information_schema checks rather than "ADD COLUMN IF NOT
-- EXISTS" / "ADD UNIQUE INDEX IF NOT EXISTS" — that syntax produced a hard
-- SQL syntax error on this MySQL build. Guards are needed because
-- public_favorite_id (and its auto-named unique index) already exist on dev
-- DBs where Hibernate's ddl-auto=update created them ahead of Flyway ever
-- running — see DotenvConfig.runFlywayMigrations() for the root-cause
-- writeup.
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'favorite' AND COLUMN_NAME = 'public_favorite_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE favorite ADD COLUMN public_favorite_id VARCHAR(36) NULL',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE favorite
SET public_favorite_id = UUID()
WHERE public_favorite_id IS NULL OR public_favorite_id = '';

ALTER TABLE favorite
    MODIFY public_favorite_id VARCHAR(36) NOT NULL;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'favorite' AND INDEX_NAME = 'uk_favorite_public_favorite_id'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE favorite ADD UNIQUE INDEX uk_favorite_public_favorite_id (public_favorite_id)',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- MySQL doesn't support "ADD CONSTRAINT ... CHECK ... IF NOT EXISTS", so this
-- CHECK constraint is guarded manually via information_schema instead.
SET @chk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'favorite'
      AND CONSTRAINT_NAME = 'chk_favorite_exactly_one_target'
);
SET @chk_sql := IF(@chk_exists = 0,
    'ALTER TABLE favorite ADD CONSTRAINT chk_favorite_exactly_one_target CHECK (
        (
            favorite_type = ''VENDOR''
            AND vendor_profile_id IS NOT NULL
            AND product_id IS NULL
        )
        OR
        (
            favorite_type = ''PRODUCT''
            AND product_id IS NOT NULL
            AND vendor_profile_id IS NULL
        )
    )',
    'DO 0'
);
PREPARE chk_stmt FROM @chk_sql;
EXECUTE chk_stmt;
DEALLOCATE PREPARE chk_stmt;
