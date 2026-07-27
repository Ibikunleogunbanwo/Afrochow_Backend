-- ===========================================================================
-- Afrochow Database Migration V25
-- Description: Add vendor_status enum (state machine) and food handling
--              certificate fields to vendor_profile.
--
-- Rationale:
--   The previous boolean pair (is_verified, is_active) cannot distinguish
--   between PENDING_PROFILE, PENDING_REVIEW, PROVISIONAL, VERIFIED, SUSPENDED,
--   and REJECTED states. This migration introduces a single vendor_status column
--   that represents the full lifecycle, and adds Canada-specific food handling
--   certificate tracking fields to support progressive (deferred) verification.
--
-- Backward compatibility:
--   is_verified and is_active are retained and backfilled so existing code
--   that still reads the booleans continues to work during the migration period.
--
-- Guarded via information_schema checks rather than "ADD COLUMN IF NOT
-- EXISTS" / "CREATE INDEX IF NOT EXISTS" — that syntax produced a hard SQL
-- syntax error on this MySQL build. Guards are needed because dev databases
-- already had several of these columns added ad hoc by Hibernate's
-- ddl-auto=update, which was silently standing in for Flyway — see
-- DotenvConfig.runFlywayMigrations() for the root-cause writeup.
-- ===========================================================================

-- 1. Add vendor_status column, defaulting existing rows based on current booleans
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND COLUMN_NAME = 'vendor_status'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE vendor_profile ADD COLUMN vendor_status ENUM(
        ''PENDING_PROFILE'',
        ''PENDING_REVIEW'',
        ''PROVISIONAL'',
        ''VERIFIED'',
        ''SUSPENDED'',
        ''REJECTED''
    ) NOT NULL DEFAULT ''PENDING_PROFILE''
        COMMENT ''Vendor lifecycle state machine. Replaces the is_verified + is_active boolean pair.''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND INDEX_NAME = 'idx_vendor_status'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_vendor_status ON vendor_profile (vendor_status)',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Backfill vendor_status from existing boolean columns
--    is_verified=true  → VERIFIED
--    is_verified=false, is_active=false → REJECTED (was deactivated/rejected)
--    is_verified=false, is_active=true  → PENDING_REVIEW (awaiting admin action)
UPDATE vendor_profile
SET vendor_status = CASE
    WHEN is_verified = TRUE  THEN 'VERIFIED'
    WHEN is_verified = FALSE AND is_active = FALSE THEN 'REJECTED'
    ELSE 'PENDING_REVIEW'
END;

-- 3. Add food handling certificate fields
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND COLUMN_NAME = 'food_handling_cert_url'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE vendor_profile ADD COLUMN food_handling_cert_url VARCHAR(500) NULL COMMENT ''URL to uploaded food handler certificate (PDF or image)''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND COLUMN_NAME = 'food_handling_cert_number'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE vendor_profile ADD COLUMN food_handling_cert_number VARCHAR(100) NULL COMMENT ''Certificate number as printed on the document''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND COLUMN_NAME = 'food_handling_cert_issuing_body'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE vendor_profile ADD COLUMN food_handling_cert_issuing_body VARCHAR(150) NULL COMMENT ''Issuing body e.g. FoodSafe BC, Manitoba Food Handler, CFIA''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND COLUMN_NAME = 'food_handling_cert_expiry'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE vendor_profile ADD COLUMN food_handling_cert_expiry DATETIME NULL COMMENT ''Expiry date, most Canadian food handler certs expire after 5 years''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND COLUMN_NAME = 'cert_verified_at'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE vendor_profile ADD COLUMN cert_verified_at DATETIME NULL COMMENT ''When an admin confirmed the certificate is valid''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND COLUMN_NAME = 'cert_verified_by_admin_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE vendor_profile ADD COLUMN cert_verified_by_admin_id VARCHAR(36) NULL COMMENT ''Public user ID of the admin who verified the certificate''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND INDEX_NAME = 'idx_cert_expiry'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_cert_expiry ON vendor_profile (food_handling_cert_expiry)',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- End of V25 migration
-- ===========================================================================
