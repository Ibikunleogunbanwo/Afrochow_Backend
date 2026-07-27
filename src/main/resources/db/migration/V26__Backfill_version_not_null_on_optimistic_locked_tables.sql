-- ===========================================================================
-- Afrochow Database Migration V26
-- Description: Backfill NULL `version` values and tighten the column on every
--              table that maps to a JPA entity with @Version on a primitive
--              `long` field: product, promotion, orders, payment.
--
-- Rationale:
--   When @Version was introduced on these entities, Hibernate (ddl-auto=update
--   in prod) auto-added the `version` column as NULLABLE with no default, so
--   existing rows were left with NULL. On subsequent loads Hibernate tries to
--   assign that NULL into the primitive `long` setter and throws:
--
--     org.hibernate.PropertyAccessException:
--       Null value was assigned to a property [class ...] of primitive type
--
--   That exception has been cascading through StatsService, SearchService,
--   CategoryService, and VendorMapper in production logs.
--
-- Fix:
--   1. Backfill NULL → 0 so every existing row has a valid starting version.
--   2. Alter the column to BIGINT NOT NULL DEFAULT 0 so any row inserted by
--      paths that bypass Hibernate (raw SQL, future migrations, seed scripts)
--      still satisfies the primitive mapping.
--
-- Idempotency note:
--   This migration is safe to re-run on databases where the column is already
--   NOT NULL — MODIFY simply re-asserts the definition and the UPDATE affects
--   zero rows.
-- ===========================================================================

-- ── product ────────────────────────────────────────────────────────────────
UPDATE product SET version = 0 WHERE version IS NULL;
ALTER TABLE product
    MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0
    COMMENT 'Optimistic-locking version (JPA @Version on primitive long)';

-- ── promotion ──────────────────────────────────────────────────────────────
UPDATE promotion SET version = 0 WHERE version IS NULL;
ALTER TABLE promotion
    MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0
    COMMENT 'Optimistic-locking version (JPA @Version on primitive long)';

-- ── orders ─────────────────────────────────────────────────────────────────
-- Table is named `orders` because `order` is a reserved word in SQL.
UPDATE orders SET version = 0 WHERE version IS NULL;
ALTER TABLE orders
    MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0
    COMMENT 'Optimistic-locking version (JPA @Version on primitive long)';

-- ── payment ────────────────────────────────────────────────────────────────
UPDATE payment SET version = 0 WHERE version IS NULL;
ALTER TABLE payment
    MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0
    COMMENT 'Optimistic-locking version (JPA @Version on primitive long)';

-- ===========================================================================
-- End of V26 migration
-- ===========================================================================
