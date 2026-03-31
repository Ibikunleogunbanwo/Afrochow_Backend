-- Set DB-level DEFAULT 'SAME_DAY' on product.schedule_type so raw SQL inserts
-- and any path that bypasses Hibernate Builder.Default still get the correct value.
ALTER TABLE product
    MODIFY COLUMN schedule_type VARCHAR(20) NOT NULL DEFAULT 'SAME_DAY';
