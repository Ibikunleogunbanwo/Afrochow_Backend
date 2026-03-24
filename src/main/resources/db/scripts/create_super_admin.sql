-- =====================================================
-- CREATE SUPER ADMIN — AFROCHOW PLATFORM
-- =====================================================
-- Run this ONCE on a fresh database to bootstrap the
-- first super admin account. Use that account to
-- create all other admins via:
--   POST /api/auth/register/admin
--
-- DEFAULT CREDENTIALS (change immediately after login):
--   Email    : superadmin@afrochow.com
--   Password : Admin@123456
--
-- BCrypt hash (12 rounds) for Admin@123456:
--   $2a$12$w80Q6w4bDxvdScGaCzX4CuDs0.C7T6cR9fnD7oS8oCKlydQ.EcToW
--
-- SECURITY REMINDERS:
--   1. Change the password immediately after first login.
--   2. Run this script only once.
--   3. Delete or restrict access to this file after use.
-- =====================================================

-- =====================================================
-- SAFETY GUARD — skip silently if already exists
-- =====================================================
SET @exists = (SELECT COUNT(*) FROM users WHERE email = 'superadmin@afrochow.com');

-- =====================================================
-- 1. INSERT USER ROW
-- =====================================================
-- public_user_id format (from User.onPrePersist):
--   role.prefix + "-" + first 12 chars of UUID (no dashes)
--   SUPERADMIN prefix = "SPA"  →  "SPA-" + 12 chars = 16 chars total
--
-- role must be SUPERADMIN so Spring Security hasRole("SUPERADMIN")
-- passes on the /auth/register/admin endpoint.
-- =====================================================
INSERT INTO users (
    public_user_id,
    username,
    email,
    password,
    first_name,
    last_name,
    phone,
    role,
    is_active,
    email_verified,
    accept_terms,
    created_at,
    updated_at
)
SELECT
    'SPA-000000000001',                                                        -- 16 chars: "SPA-" + 12 digits
    'systemadmin',
    'superadmin@afrochow.com',
    '$2a$12$w80Q6w4bDxvdScGaCzX4CuDs0.C7T6cR9fnD7oS8oCKlydQ.EcToW',         -- Admin@123456
    'System',
    'Administrator',
    '15870000000',                                                             -- stored as digits only (setPhone strips formatting)
    'SUPERADMIN',
    true,
    true,
    true,
    NOW(),
    NOW()
WHERE @exists = 0;

-- =====================================================
-- 2. INSERT ADMIN PROFILE ROW
-- =====================================================
-- employee_id: column length = 8, must be exactly 8 numeric digits
--              (mirrors generate8DigitEmployeeId() which produces 10000000–99999999)
-- access_level = SUPER_ADMIN grants all permissions
-- =====================================================
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
)
SELECT
    u.user_id,
    'MANAGEMENT',
    'SUPER_ADMIN',
    '10000001',                   -- 8 numeric digits
    true,
    true,
    true,
    true,
    true,
    true,
    0,
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'superadmin@afrochow.com'
  AND NOT EXISTS (
      SELECT 1 FROM admin_profile ap WHERE ap.user_id = u.user_id
  );

-- =====================================================
-- VERIFICATION — run after the inserts to confirm
-- =====================================================
-- SELECT
--     u.user_id,
--     u.public_user_id,
--     u.username,
--     u.email,
--     u.role,
--     u.is_active,
--     u.email_verified,
--     ap.access_level,
--     ap.employee_id,
--     ap.department,
--     ap.can_verify_vendors,
--     ap.can_manage_users,
--     ap.can_view_reports,
--     ap.can_manage_payments,
--     ap.can_manage_categories,
--     ap.can_resolve_disputes
-- FROM users u
-- JOIN admin_profile ap ON u.user_id = ap.user_id
-- WHERE u.email = 'superadmin@afrochow.com';

-- =====================================================
-- HOW TO RUN
-- =====================================================
-- Option 1: MySQL CLI
--   mysql -h <host> -P <port> -u <user> -p <database> < create_super_admin.sql
--
-- Option 2: Railway MySQL plugin (via DB client)
--   Connect with the Railway connection string and paste this script.
--
-- Option 3: DBeaver / TablePlus / MySQL Workbench
--   Open a query window, paste, and execute.
--
-- Option 4: Railway one-off command
--   Railway CLI → run a container with mysql client pointing at the DB.
-- =====================================================

-- =====================================================
-- AFTER FIRST LOGIN
-- =====================================================
-- 1. POST /api/auth/login  { "email": "superadmin@afrochow.com", "password": "Admin@123456" }
-- 2. Use the returned JWT to call:
--    POST /api/auth/register/admin   (Authorization: Bearer <token>)
--    to create real admin accounts for your team.
-- 3. Change the superadmin password immediately:
--    POST /api/auth/change-password
-- =====================================================
