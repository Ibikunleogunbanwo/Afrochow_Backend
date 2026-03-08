-- =====================================================
-- CREATE FIRST ADMIN USER FOR AFROCHOW PLATFORM
-- =====================================================
-- This script creates the initial super admin account
-- that can be used to create additional admin users
-- via the /auth/register/admin endpoint.
--
-- DEFAULT CREDENTIALS:
-- Email: admin@afrochow.com
-- Password: REDACTED
--
-- IMPORTANT SECURITY NOTES:
-- 1. Change the default password immediately after first login
-- 2. This script should ONLY be run once during initial setup
-- 3. Delete or secure this file after running it
-- 4. Use strong passwords in production
-- =====================================================

-- Insert the admin user
-- Password: REDACTED
-- BCrypt hash with 12 rounds (matches SecurityConfig settings)
-- Generated using PasswordHashGenerator utility
INSERT INTO users (
    public_user_id,
    email,
    password,
    first_name,
    last_name,
    phone,
    role,
    is_active,
    created_at,
    updated_at
) VALUES (
    'ADM-00000000-0000-0000-0000-000000000001',
    'admin@afrochow.com',
    'REDACTED_HASH',  -- Password: REDACTED (BCrypt 12 rounds)
    'System',
    'Administrator',
    '+1-587-000-0000',
    'ADMIN',
    true,
    NOW(),
    NOW()
);

-- Insert the admin profile
-- Get the user_id we just created
SET @admin_user_id = LAST_INSERT_ID();

INSERT INTO admin_profile (
    user_id,
    department,
    access_level,
    employee_id,
    can_verify_vendors,
    can_manage_users,
    can_view_reports,
    can_manage_payments,
    can_manage_categories,
    can_resolve_disputes,
    total_actions_performed,
    created_at,
    updated_at
) VALUES (
    @admin_user_id,
    'OPERATIONS',
    'SUPER_ADMIN',
    'EMP-ADMIN-001',
    true,  -- Full permissions for super admin
    true,
    true,
    true,
    true,
    true,
    0,
    NOW(),
    NOW()
);

-- =====================================================
-- VERIFICATION QUERY
-- =====================================================
-- Run this to verify the admin was created successfully:
-- SELECT u.*, ap.* FROM users u
-- JOIN admin_profile ap ON u.user_id = ap.user_id
-- WHERE u.email = 'admin@afrochow.com';
-- =====================================================

-- =====================================================
-- HOW TO USE THIS SCRIPT:
-- =====================================================
-- Option 1: MySQL Command Line
--   mysql -u [username] -p [database_name] < create_first_admin.sql
--
-- Option 2: MySQL Workbench
--   1. Open MySQL Workbench
--   2. Connect to your database
--   3. File > Open SQL Script > Select this file
--   4. Execute (lightning bolt icon or Ctrl+Shift+Enter)
--
-- Option 3: DBeaver
--   1. Connect to your database
--   2. Open SQL Editor
--   3. Copy and paste this script
--   4. Execute (Ctrl+Enter)
--
-- After running this script, you can login with:
-- POST /api/auth/login
-- {
--   "email": "admin@afrochow.com",
--   "password": "REDACTED"
-- }
-- =====================================================

-- =====================================================
-- HOW TO GENERATE NEW PASSWORD HASHES:
-- =====================================================
-- If you need to generate a new BCrypt hash for a different password:
--
-- Run the PasswordHashGenerator utility:
--   ./mvnw compile exec:java -Dexec.mainClass="util.com.afrochow.PasswordHashGenerator"
--
-- This will generate BCrypt hashes (12 rounds) that match your SecurityConfig settings.
-- Copy the generated hash and replace the password value in this script.
-- =====================================================
