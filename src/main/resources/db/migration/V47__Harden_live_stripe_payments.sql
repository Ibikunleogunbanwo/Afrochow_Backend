SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE vendor_profile ADD COLUMN stripe_charges_enabled BOOLEAN NOT NULL DEFAULT FALSE',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'vendor_profile'
      AND column_name = 'stripe_charges_enabled'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE vendor_profile ADD COLUMN stripe_payouts_enabled BOOLEAN NOT NULL DEFAULT FALSE',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'vendor_profile'
      AND column_name = 'stripe_payouts_enabled'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE vendor_profile ADD COLUMN stripe_requirements_disabled_reason VARCHAR(255) NULL',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'vendor_profile'
      AND column_name = 'stripe_requirements_disabled_reason'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE vendor_profile
SET stripe_charges_enabled = TRUE,
    stripe_payouts_enabled = TRUE
WHERE stripe_onboarding_complete = TRUE
  AND stripe_account_id IS NOT NULL
  AND stripe_account_id <> ''
  AND stripe_charges_enabled = FALSE
  AND stripe_payouts_enabled = FALSE;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE orders ADD COLUMN checkout_idempotency_key VARCHAR(80) NULL',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND column_name = 'checkout_idempotency_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE orders ADD UNIQUE KEY uk_orders_checkout_idempotency_key (checkout_idempotency_key)',
        'DO 0')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND index_name = 'uk_orders_checkout_idempotency_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS stripe_webhook_event (
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    last_error TEXT NULL,
    PRIMARY KEY (event_id),
    INDEX idx_stripe_webhook_event_status (status),
    INDEX idx_stripe_webhook_event_created_at (created_at)
);
