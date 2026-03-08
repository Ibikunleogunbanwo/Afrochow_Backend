-- =====================================================
-- FIX ADDRESS-VENDOR RELATIONSHIP
-- =====================================================
-- This script fixes the orphan vendor_id column in the address table
--
-- PROBLEM:
-- - Address table has an orphan vendor_id column
-- - VendorProfile owns the relationship via address_id column
-- - The vendor_id column is unnecessary and should be removed
--
-- SOLUTION:
-- - Drop the vendor_id foreign key constraint (if exists)
-- - Drop the vendor_id column from address table
-- =====================================================

USE AFROCHOW;

-- Step 1: Check if the foreign key exists and drop it
SET @fk_exists = (SELECT COUNT(*)
                  FROM information_schema.TABLE_CONSTRAINTS
                  WHERE CONSTRAINT_SCHEMA = 'AFROCHOW'
                    AND TABLE_NAME = 'address'
                    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                    AND CONSTRAINT_NAME LIKE '%vendor%');

SET @drop_fk_sql = IF(@fk_exists > 0,
                      'ALTER TABLE address DROP FOREIGN KEY (SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = "AFROCHOW" AND TABLE_NAME = "address" AND CONSTRAINT_TYPE = "FOREIGN KEY" AND CONSTRAINT_NAME LIKE "%vendor%")',
                      'SELECT "No vendor foreign key to drop"');

-- Note: The above dynamic SQL won't work directly in MySQL
-- You'll need to drop the FK manually if it exists

-- Step 2: Drop the vendor_id column if it exists
SET @column_exists = (SELECT COUNT(*)
                      FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = 'AFROCHOW'
                        AND TABLE_NAME = 'address'
                        AND COLUMN_NAME = 'vendor_id');

-- Drop column only if it exists
SET @drop_column_sql = IF(@column_exists > 0,
                          'ALTER TABLE address DROP COLUMN vendor_id',
                          'SELECT "Column vendor_id does not exist"');

PREPARE stmt FROM @drop_column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================
-- VERIFICATION
-- =====================================================
-- Run these queries to verify the fix:

-- 1. Check address table structure (should NOT have vendor_id)
DESCRIBE address;

-- 2. Check vendor_profile table structure (should have address_id)
DESCRIBE vendor_profile;

-- 3. Verify foreign keys on address table
SELECT
    CONSTRAINT_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'AFROCHOW'
  AND TABLE_NAME = 'address'
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- Expected result: Only customer_profile_id FK, NO vendor_id FK

-- 4. Verify foreign keys on vendor_profile table
SELECT
    CONSTRAINT_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'AFROCHOW'
  AND TABLE_NAME = 'vendor_profile'
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- Expected result: user_id FK and address_id FK

-- =====================================================
-- MANUAL ALTERNATIVE
-- =====================================================
-- If the dynamic SQL doesn't work, run these commands manually:

-- 1. Find and drop the foreign key constraint
-- SHOW CREATE TABLE address;
-- ALTER TABLE address DROP FOREIGN KEY fk_address_vendor; -- Use actual FK name

-- 2. Drop the vendor_id column
-- ALTER TABLE address DROP COLUMN vendor_id;

-- 3. Verify
-- DESCRIBE address;
-- =====================================================

SELECT 'Address-Vendor relationship fix completed!' AS status;
