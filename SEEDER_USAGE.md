# Database Seeder Usage Guide

## Overview
The VendorDataSeeder automatically populates your database with sample vendor data including:
- **5 Vendor Profiles** with Nigerian restaurants
- **Categories** (African Kitchen, FarmProduce, African Soup, Smallchops/Cake, Naija Bread)
- **3-5 Products per vendor** with Nigerian dishes (Jollof Rice, Egusi Soup, Suya, etc.)
- **2-4 Reviews per vendor** with sample customer feedback
- **User accounts** for both vendors and customers

## Features
- **Uses existing image URLs** from Cloudinary (as provided in data.json)
- **Maintains database schema** - No changes to existing structure or naming conventions
- **Sample Nigerian phone numbers** and addresses in Lagos
- **Operating hours** - Vendors open Monday-Saturday 9:00-22:00, closed Sunday
- **Automatic UUID generation** for all public IDs

## How It Works

The seeder is a Spring Boot `CommandLineRunner` that:
1. **Checks if data exists** - Only runs if the database is empty (no existing vendors)
2. **Creates vendor users** with role `VENDOR`
3. **Creates customer users** for reviews with role `CUSTOMER`
4. **Generates categories** if they don't exist
5. **Creates vendor profiles** with addresses and operating hours
6. **Adds products** with Nigerian dishes
7. **Creates reviews** with ratings and comments

## Running the Seeder

### Method 1: Automatic (On Application Startup)
The seeder runs automatically when you start the application if the vendor table is empty:

```bash
mvn spring-boot:run
```

Or if using the JAR:
```bash
java -jar target/afrochow-0.0.1-SNAPSHOT.jar
```

### Method 2: Manual Trigger
If you want to manually trigger the seeder after clearing the database:

1. Clear the database tables:
```sql
-- Be careful! This will delete all data
TRUNCATE TABLE review CASCADE;
TRUNCATE TABLE product CASCADE;
TRUNCATE TABLE vendor_profile CASCADE;
TRUNCATE TABLE users CASCADE;
TRUNCATE TABLE category CASCADE;
TRUNCATE TABLE address CASCADE;
```

2. Restart the application - the seeder will detect the empty database and populate it.

## Sample Data Created

### Vendors
1. **Noodle House** - African Kitchen
2. **The Hungry Bear** - FarmProduce
3. **Mama Mia's Pizzeria** - African Soup
4. **The Cheesy Grill** - Smallchops/Cake
5. **Burger Barn** - Naija Bread

### Products (Sample)
- Jollof Rice
- Egusi Soup
- Pounded Yam
- Suya
- Chin Chin
- Moin Moin
- Akara
- Ewa Agoyin
- Pepper Soup
- Gizdodo

### Sample Login Credentials
All seeded accounts use the default password: `password123`

**Vendor Accounts:**
- noodlehouse@afrochow.com
- thehungrybear@afrochow.com
- mamamiaspizzeria@afrochow.com
- thecheesygrill@afrochow.com
- burgerbarn@afrochow.com

**Customer Accounts:**
- john.doe00@customer.com
- jane.smith01@customer.com
- mike.johnson02@customer.com
- (and more...)

## Customization

### Adding More Vendors
Edit the arrays in `VendorDataSeeder.java`:

```java
String[] vendorNames = {"Noodle House", "Your Restaurant Name", ...};
String[] descriptions = {"Description here", ...};
String[] locations = {"Your Address", ...};
String[] cuisineTypes = {"African Kitchen", ...};
```

### Changing Default Password
Modify the password in the `createVendorUser` and `createReviewerUser` methods:

```java
.password(passwordEncoder.encode("your-password-here"))
```

### Adjusting Operating Hours
Modify the `createDefaultOperatingHours()` method to change business hours.

## Database Schema Compatibility

The seeder respects the existing schema:

| Entity | Fields Used |
|--------|-------------|
| **VendorProfile** | restaurantName, description, cuisineType, logoUrl, isVerified, isActive, deliveryFee, offersDelivery, offersPickup, preparationTime |
| **Product** | name, description, price, imageUrl, available, preparationTimeMinutes |
| **Review** | rating (1-5), comment, isVisible, helpfulCount |
| **User** | email, password, firstName, lastName, phone, role, emailVerified, isActive |
| **Address** | addressLine, city, province, country, postalCode |
| **Category** | name, description, isActive, displayOrder |

## Troubleshooting

### Seeder Not Running
- **Check logs**: Look for "Database already seeded with vendor data, skipping seeder..."
- **Solution**: The database already has vendor data. Clear it if you want to re-seed.

### Compilation Errors
- **Run**: `mvn clean compile`
- **Check**: All repository interfaces are available
- **Verify**: Role enum is imported correctly

### Duplicate Key Errors
- **Cause**: Unique constraint violations (email, phone, publicUserId)
- **Solution**: Clear the database completely before re-running

## Disabling the Seeder

To disable the seeder, comment out the `@Component` annotation:

```java
// @Component  // Commented out to disable
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class VendorDataSeeder implements CommandLineRunner {
    ...
}
```

Or add a profile condition:
```java
@Component
@Profile("dev") // Only run in dev profile
public class VendorDataSeeder implements CommandLineRunner {
    ...
}
```

## Notes
- The seeder uses **sample images from Cloudinary** as provided in your data.json
- All **timestamps** are automatically set via `@CreationTimestamp`
- **PublicUserIds** are auto-generated using the User entity's `@PrePersist` hook
- **Operating hours** are stored as JSON in the database
- The seeder is **idempotent** - it won't duplicate data if run multiple times on an empty database

## Next Steps
After seeding:
1. Access the vendors via the API: `GET /api/vendors`
2. View products: `GET /api/products`
3. Check reviews: `GET /api/reviews`
4. Test authentication with seeded credentials

---

**Location**: `src/main/java/com/afrochow/seeder/VendorDataSeeder.java`
