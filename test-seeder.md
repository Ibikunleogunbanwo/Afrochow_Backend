# Quick Test Commands

## 1. Compile the Project
```bash
mvn clean compile
```
Expected: BUILD SUCCESS

## 2. Check Database Connection
```bash
mysql -u Superadmin -p'905v5fI'"'"',4pfumJ1~' -h localhost afrochowdb -e "SHOW TABLES;"
```

## 3. Run the Application (will trigger seeder)
```bash
mvn spring-boot:run
```

Look for log messages:
- "Starting vendor data seeding..."
- "Created vendor: Noodle House"
- "Created vendor: The Hungry Bear"
- ...
- "Vendor data seeding completed successfully!"

## 4. Verify Data in Database
```bash
mysql -u Superadmin -p'905v5fI'"'"',4pfumJ1~' -h localhost afrochowdb << 'SQL'
-- Check vendors
SELECT COUNT(*) as vendor_count FROM vendor_profile;

-- Check products
SELECT COUNT(*) as product_count FROM product;

-- Check reviews
SELECT COUNT(*) as review_count FROM review;

-- View sample vendor
SELECT restaurant_name, cuisine_type, is_verified 
FROM vendor_profile 
LIMIT 3;

-- View sample products
SELECT p.name, p.price, vp.restaurant_name as vendor
FROM product p
JOIN vendor_profile vp ON p.vendor_profile_id = vp.id
LIMIT 5;
SQL
```

## 5. Test API Endpoints (after app is running)
```bash
# Get all vendors
curl http://localhost:8080/api/vendors

# Get all products
curl http://localhost:8080/api/products

# Get all categories
curl http://localhost:8080/api/categories
```

## 6. Test Login with Seeded Account
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "noodlehouse@afrochow.com",
    "password": "password123"
  }'
```

Expected: JWT token response

