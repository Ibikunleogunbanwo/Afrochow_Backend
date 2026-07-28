-- ===========================================================================
-- Afrochow Database Migration V36
-- Description: Track the Stripe Connect columns on vendor_profile that the
--              entity has mapped for a while (see VendorProfile.stripeAccountId
--              / stripeOnboardingComplete) but that never got a Flyway
--              migration of their own.
--
-- Same root cause as V25/V35: vendor_profile predates Flyway and was created/
-- extended via Hibernate's ddl-auto=update before prod was locked to
-- ddl-auto=validate, so environments that already went through that history
-- (including current prod) already have these columns. A fresh database —
-- new dev instance, rebuilt staging, CI, disaster-recovery restore — created
-- straight from Flyway migrations would NOT have them and would fail
-- ddl-auto=validate at startup with:
--   Schema-validation: missing column [stripe_account_id] in table [vendor_profile]
--
-- Guarded via information_schema checks (same pattern as V25/V27/V35) so this
-- is a no-op wherever the columns already exist.
-- ===========================================================================

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND COLUMN_NAME = 'stripe_account_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE vendor_profile ADD COLUMN stripe_account_id VARCHAR(100) NULL COMMENT ''Stripe Connect account ID linked to this vendor''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendor_profile' AND COLUMN_NAME = 'stripe_onboarding_complete'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE vendor_profile ADD COLUMN stripe_onboarding_complete TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''Whether the vendor completed Stripe Connect onboarding''',
    'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===========================================================================
-- End of V36 migration
-- ===========================================================================
