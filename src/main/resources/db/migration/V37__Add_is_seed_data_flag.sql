-- ===========================================================================
-- Afrochow Database Migration V37
-- Description: Add an is_seed_data flag to every table CompleteFinalSeeder
--              writes to, and backfill it for the existing seeded rows, now
--              that real vendors are registering alongside the demo data.
--
-- CompleteFinalSeeder creates 20 demo vendors (with their user accounts,
-- products, addresses, and synthetic reviews) and 10 demo customers on first
-- boot against an empty database. It has now also been pinned to
-- @Profile("!prod") and made to set isSeedData=true on every row it creates
-- going forward, but the rows it already wrote in the past have no such flag
-- to backfill from -- they need to be identified by fingerprint instead:
--
--   vendor_profile : businessLicenseUrl always points at the fake domain
--                     storage.cloud.example.com (real vendors upload to
--                     Cloudinary, res.cloudinary.com)
--   customer_profile: either the 10 hardcoded seed customer emails, or one
--                      of the dynamically-created "reviewer" accounts, which
--                      always use the @customer.com domain
--   users, product, address, review: no fingerprint of their own -- derived
--                      by joining back to the now-flagged vendor_profile /
--                      customer_profile rows above
--
-- Guarded via information_schema checks (same pattern as V25/V27/V35/V36) so
-- the ALTER TABLE statements are a no-op wherever the column already exists.
-- The backfill UPDATEs are plain idempotent WHERE-matches, safe to run more
-- than once.
-- ===========================================================================

-- ── 1. Add columns ──────────────────────────────────────────────────────

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND COLUMN_NAME = 'is_seed_data');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE vendor_profile ADD COLUMN is_seed_data BOOLEAN NOT NULL DEFAULT FALSE', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customer_profile' AND COLUMN_NAME = 'is_seed_data');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE customer_profile ADD COLUMN is_seed_data BOOLEAN NOT NULL DEFAULT FALSE', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'is_seed_data');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE users ADD COLUMN is_seed_data BOOLEAN NOT NULL DEFAULT FALSE', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'is_seed_data');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE product ADD COLUMN is_seed_data BOOLEAN NOT NULL DEFAULT FALSE', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'address' AND COLUMN_NAME = 'is_seed_data');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE address ADD COLUMN is_seed_data BOOLEAN NOT NULL DEFAULT FALSE', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'is_seed_data');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE review ADD COLUMN is_seed_data BOOLEAN NOT NULL DEFAULT FALSE', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 2. Backfill vendor_profile and customer_profile from their own fingerprints ──

UPDATE vendor_profile
SET is_seed_data = TRUE
WHERE business_license_url LIKE '%storage.cloud.example.com%';

UPDATE customer_profile cp
JOIN users u ON u.user_id = cp.user_id
SET cp.is_seed_data = TRUE
WHERE u.email LIKE '%@customer.com'
   OR u.email IN (
        'adaeze.okafor@gmail.com', 'emeka.nwosu@gmail.com', 'ngozi.eze@gmail.com',
        'chidi.obi@gmail.com', 'amina.diallo@gmail.com', 'kofi.mensah@gmail.com',
        'fatima.balogun@gmail.com', 'tunde.adeyemi@gmail.com', 'chisom.igwe@gmail.com',
        'bola.afolabi@gmail.com'
   );

-- ── 3. Backfill users, product, address, review by joining back to the above ──

UPDATE users u
JOIN vendor_profile vp ON vp.user_id = u.user_id
SET u.is_seed_data = TRUE
WHERE vp.is_seed_data = TRUE;

UPDATE users u
JOIN customer_profile cp ON cp.user_id = u.user_id
SET u.is_seed_data = TRUE
WHERE cp.is_seed_data = TRUE;

UPDATE product p
JOIN vendor_profile vp ON vp.id = p.vendor_profile_id
SET p.is_seed_data = TRUE
WHERE vp.is_seed_data = TRUE;

UPDATE address a
JOIN vendor_profile vp ON vp.address_id = a.address_id
SET a.is_seed_data = TRUE
WHERE vp.is_seed_data = TRUE;

UPDATE address a
JOIN customer_profile cp ON cp.customer_profile_id = a.customer_profile_id
SET a.is_seed_data = TRUE
WHERE cp.is_seed_data = TRUE;

UPDATE review r
JOIN vendor_profile vp ON vp.id = r.vendor_profile_id
SET r.is_seed_data = TRUE
WHERE vp.is_seed_data = TRUE;

-- ===========================================================================
-- End of V37 migration
-- ===========================================================================
