-- Splits the single `discount` column into the two buckets the payout math needs.
--
-- Promos are vendor-funded: the discount comes out of the vendor's take, so the
-- platform's commission is charged on the food the vendor actually got paid for.
-- That makes the allocation load-bearing rather than cosmetic:
--   * food_discount     (PERCENTAGE / FIXED_AMOUNT) reduces the commission base
--   * delivery_discount (FREE_DELIVERY) waives the vendor's delivery fee and
--                       leaves the commission base untouched
--
-- `discount` is retained as the display total and always equals the sum of the two.
--
-- Backfill treats all existing rows as food discounts. That is correct for
-- PERCENTAGE and FIXED_AMOUNT and wrong for FREE_DELIVERY, but there is no live
-- order data at the time of writing (seeded data only), and the promo type is not
-- recoverable from the orders table alone.

ALTER TABLE orders
    ADD COLUMN food_discount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN delivery_discount DECIMAL(10, 2) NOT NULL DEFAULT 0.00;

UPDATE orders
SET food_discount = COALESCE(discount, 0.00)
WHERE COALESCE(discount, 0.00) <> 0.00;