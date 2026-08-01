-- ===========================================================================
-- Afrochow Database Migration V48
-- Description: Remove synthetic customer/reviewer accounts from the production
--              showroom dataset.
--
-- The seeded vendor catalogue is useful for browsing by city, but the cloned
-- reviewer/customer users inflate admin user counts. Keep seed vendors/products
-- intact and remove only seed CUSTOMER users that have no orders and no real
-- reviews. This preserves real customer accounts and any customer that has
-- transactional history.
-- ===========================================================================

DROP TABLE IF EXISTS seed_customer_users_to_remove;

CREATE TABLE seed_customer_users_to_remove AS
SELECT
    u.user_id,
    cp.customer_profile_id
FROM users u
JOIN customer_profile cp ON cp.user_id = u.user_id
WHERE u.role = 'CUSTOMER'
  AND u.is_seed_data = TRUE
  AND cp.is_seed_data = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM orders o
      WHERE o.customer_profile_id = cp.customer_profile_id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM review real_review
      WHERE real_review.user_id = u.user_id
        AND real_review.is_seed_data = FALSE
  );

DELETE r
FROM review r
JOIN seed_customer_users_to_remove sc ON sc.user_id = r.user_id
WHERE r.is_seed_data = TRUE;

DELETE f
FROM favorite f
JOIN seed_customer_users_to_remove sc ON sc.customer_profile_id = f.customer_profile_id;

DELETE n
FROM notification n
JOIN seed_customer_users_to_remove sc ON sc.user_id = n.user_id;

DELETE rt
FROM refresh_tokens rt
JOIN seed_customer_users_to_remove sc ON sc.user_id = rt.user_id;

DELETE evt
FROM email_verification_token evt
JOIN seed_customer_users_to_remove sc ON sc.user_id = evt.user_id;

DELETE prt
FROM password_reset_tokens prt
JOIN seed_customer_users_to_remove sc ON sc.user_id = prt.user_id;

DELETE pu
FROM promotion_usage pu
JOIN seed_customer_users_to_remove sc ON sc.user_id = pu.user_id;

DELETE a
FROM address a
JOIN seed_customer_users_to_remove sc ON sc.customer_profile_id = a.customer_profile_id
WHERE a.is_seed_data = TRUE;

DELETE cp
FROM customer_profile cp
JOIN seed_customer_users_to_remove sc ON sc.customer_profile_id = cp.customer_profile_id;

DELETE u
FROM users u
JOIN seed_customer_users_to_remove sc ON sc.user_id = u.user_id;

DROP TABLE IF EXISTS seed_customer_users_to_remove;

-- ===========================================================================
-- End of V48 migration
-- ===========================================================================
