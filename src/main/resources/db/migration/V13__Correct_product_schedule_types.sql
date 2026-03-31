-- ============================================================
-- Correct schedule_type on all existing products.
--
-- Only "Cakes" and "African Soups" require advance ordering.
-- Everything else is same-day (ready within preparation time).
--
-- A prior backfill incorrectly set most products to ADVANCE_ORDER.
-- This migration corrects the data to match the seeder intent.
-- ============================================================

-- Step 1: Reset ALL products to SAME_DAY (safe baseline)
UPDATE product
SET schedule_type       = 'SAME_DAY',
    advance_notice_hours = NULL;

-- Step 2: Re-apply ADVANCE_ORDER only for the two categories that need it
UPDATE product p
    JOIN category c ON p.category_id = c.category_id
SET p.schedule_type        = 'ADVANCE_ORDER',
    p.advance_notice_hours = 24
WHERE c.name IN ('Cakes', 'African Soups');
