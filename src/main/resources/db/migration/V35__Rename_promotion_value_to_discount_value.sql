-- ===========================================================================
-- Aligns the `promotion` table's discount-amount column with the entity's
-- @Column(name = "discount_value") mapping (see Promotion.java).
--
-- The `promotion` table predates any Flyway migration -- it was created via
-- Hibernate's ddl-auto=update on this environment before prod was locked to
-- ddl-auto=validate, back when the entity field mapped straight to a column
-- literally named `value`. `value` is a reserved word in H2 (the test
-- profile's DB), which broke test-suite schema generation, so the entity was
-- remapped to `discount_value` -- fine for MySQL, but it left production's
-- already-existing table out of sync with the new mapping, causing
-- ddl-auto=validate to fail at startup with:
--   Schema-validation: missing column [discount_value] in table [promotion]
--
-- Guarded via information_schema checks (same pattern as V27) so this is a
-- no-op wherever the table doesn't exist yet, or already has the new column
-- name (e.g. a fresh dev DB created directly from the current entity).
-- ===========================================================================

SET @table_exists := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion');
SET @has_value_col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion' AND COLUMN_NAME = 'value');
SET @has_discount_value_col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion' AND COLUMN_NAME = 'discount_value');

SET @sql := IF(@table_exists = 1 AND @has_value_col = 1 AND @has_discount_value_col = 0,
    'ALTER TABLE promotion CHANGE COLUMN `value` discount_value DECIMAL(10,2) NOT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
