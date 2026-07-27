-- Creates the table backing com.afrochow.waitlist.model.WaitlistEntry.
-- The entity/controller/service/repository were already in the codebase with
-- no migration ever committed to create the underlying table — on any database
-- where Hibernate didn't implicitly create it via ddl-auto=update (i.e. every
-- environment where Flyway is the real source of truth), waitlist signups were
-- failing outright with a "table doesn't exist" error. This backfills it.
CREATE TABLE IF NOT EXISTS waitlist_entries (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    public_waitlist_id VARCHAR(80)  NOT NULL,
    name               VARCHAR(120) NOT NULL,
    email              VARCHAR(180) NOT NULL,
    city               VARCHAR(120) NULL,
    role               VARCHAR(30)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_waitlist_public_id UNIQUE (public_waitlist_id),
    CONSTRAINT uk_waitlist_email_role UNIQUE (email, role)
);

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'waitlist_entries' AND INDEX_NAME = 'idx_waitlist_email');
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_waitlist_email ON waitlist_entries (email)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'waitlist_entries' AND INDEX_NAME = 'idx_waitlist_role');
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_waitlist_role ON waitlist_entries (role)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'waitlist_entries' AND INDEX_NAME = 'idx_waitlist_city');
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_waitlist_city ON waitlist_entries (city)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
