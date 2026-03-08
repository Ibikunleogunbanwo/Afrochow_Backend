# Afrochow Backend - Technical Documentation

> **Version**: 0.0.1-SNAPSHOT
> **Framework**: Spring Boot 4.0.0
> **Java Version**: 21
> **Last Updated**: January 2026

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Technology Stack](#technology-stack)
3. [Architecture Overview](#architecture-overview)
4. [Domain Models](#domain-models)
5. [API Endpoints](#api-endpoints)
6. [Security & Authentication](#security--authentication)
7. [Business Services](#business-services)
8. [Database Schema](#database-schema)
9. [Configuration](#configuration)
10. [Development Guide](#development-guide)

---

## Project Overview

**Afrochow** is a food delivery platform connecting customers with African food restaurants. The backend provides a comprehensive REST API for order management, vendor operations, customer profiles, payments, and administrative functions.

### Key Features

- 🔐 JWT-based authentication with encrypted tokens
- 👥 Multi-role user system (Customer, Vendor, Admin)
- 🍽️ Product catalog with categories and search
- 📦 End-to-end order management
- 💳 Payment processing with refund support
- ⭐ Product and vendor review system
- 📧 Email verification and password reset
- 🔒 Account lockout and rate limiting
- 📊 Admin analytics and reporting
- 📱 Real-time notifications

---

## Technology Stack

### Core Technologies

| Component | Technology | Version |
|-----------|-----------|---------|
| **Framework** | Spring Boot | 4.0.0 |
| **Language** | Java | 21 |
| **Database** | MySQL | 8.0+ |
| **ORM** | Hibernate (JPA) | 7.x |
| **Security** | Spring Security | 7.0.0 |
| **Migration** | Flyway | Latest |
| **Cache** | Caffeine | Latest |
| **API Docs** | SpringDoc OpenAPI | 3.0.0 |

### Key Dependencies

```xml
<!-- Security & Authentication -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Email -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<!-- Caching -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

---

## Architecture Overview

### Package Structure

```
com.afrochow
├── admin/              # Admin management module
│   ├── controller/
│   ├── model/
│   ├── repository/
│   ├── service/
│   └── dto/
├── auth/               # Authentication module
│   ├── controller/
│   ├── service/
│   └── dto/
├── category/           # Product categories
├── customer/           # Customer profile management
├── vendor/             # Vendor profile & product management
├── product/            # Product catalog
├── order/              # Order processing
├── orderline/          # Order line items
├── payment/            # Payment processing
├── review/             # Review system
├── notification/       # Notification system
├── address/            # Address management
├── user/               # Base user model
├── security/           # Security components (JWT, filters, etc.)
├── config/             # Configuration classes
├── common/             # Shared utilities, enums, exceptions
├── email/              # Email service
├── image/              # Image upload handling
└── search/             # Search functionality
```

### Architecture Patterns

1. **Layered Architecture**
   - Controller → Service → Repository → Entity
   - Clear separation of concerns
   - DTOs for API contracts

2. **Repository Pattern**
   - Spring Data JPA repositories
   - Custom query methods
   - Database abstraction

3. **DTO Pattern**
   - Request DTOs for input validation
   - Response DTOs for output formatting
   - Summary DTOs for list endpoints

4. **Service Layer**
   - Business logic encapsulation
   - Transaction management
   - Cross-cutting concerns

---

## Domain Models

### Core Entity Relationships

```
User (Base)
├── CustomerProfile (1:1)
│   ├── Address (1:Many)
│   └── Order (1:Many)
├── VendorProfile (1:1)
│   ├── Address (1:1)
│   ├── Product (1:Many)
│   ├── Order (1:Many)
│   └── Review (1:Many)
└── AdminProfile (1:1)

Product
├── Category (Many:1)
├── VendorProfile (Many:1)
├── OrderLine (1:Many)
└── Review (1:Many)

Order
├── CustomerProfile (Many:1)
├── VendorProfile (Many:1)
├── Address (Many:1)
├── OrderLine (1:Many)
└── Payment (1:1)
```

### Key Models

#### User Model
```java
@Entity
public class User {
    @Id @GeneratedValue
    private Long userId;

    private String publicUserId;  // CUS-xxx, VEN-xxx, ADM-xxx

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;  // BCrypt hashed

    private String firstName;
    private String lastName;
    private String phone;
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    private Role role;  // CUSTOMER, VENDOR, ADMIN

    private boolean isActive;
    private boolean emailVerified;

    @OneToOne(mappedBy = "user", cascade = ALL)
    private CustomerProfile customerProfile;

    @OneToOne(mappedBy = "user", cascade = ALL)
    private VendorProfile vendorProfile;

    @OneToOne(mappedBy = "user", cascade = ALL)
    private AdminProfile adminProfile;
}
```

#### VendorProfile Model
```java
@Entity
public class VendorProfile {
    @Id @GeneratedValue
    private Long vendorId;

    private String publicVendorId;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Business Information
    private String restaurantName;
    private String description;
    private String cuisineType;
    private String logoUrl;
    private String bannerUrl;
    private String taxId;
    private String businessLicenseUrl;

    // Service Options
    private boolean offersDelivery;
    private boolean offersPickup;
    private BigDecimal deliveryFee;
    private BigDecimal minimumOrderAmount;
    private Integer estimatedDeliveryMinutes;
    private Integer preparationTime;

    // Operating Hours (JSON)
    @Column(columnDefinition = "TEXT")
    private String operatingHoursJson;

    // Verification
    private boolean isVerified;
    private boolean isActive;
    private LocalDateTime verifiedAt;

    // Statistics
    private Long totalOrdersCompleted;
    private BigDecimal totalRevenue;

    @OneToOne(cascade = ALL)
    private Address address;

    @OneToMany(mappedBy = "vendor")
    private List<Product> products;
}
```

#### Order Model
```java
@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue
    private Long orderId;

    private String publicOrderId;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private CustomerProfile customer;

    @ManyToOne
    @JoinColumn(name = "vendor_id")
    private VendorProfile vendor;

    @ManyToOne
    @JoinColumn(name = "delivery_address_id")
    private Address deliveryAddress;

    // Financial
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal tax;
    private BigDecimal discount;
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String specialInstructions;

    // Timestamps
    private LocalDateTime orderTime;
    private LocalDateTime confirmedAt;
    private LocalDateTime preparingAt;
    private LocalDateTime readyAt;
    private LocalDateTime outForDeliveryAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "order", cascade = ALL)
    private List<OrderLine> orderLines;

    @OneToOne(mappedBy = "order", cascade = ALL)
    private Payment payment;
}
```

#### Product Model
```java
@Entity
public class Product {
    @Id @GeneratedValue
    private Long productId;

    private String publicProductId;

    @ManyToOne
    @JoinColumn(name = "vendor_id")
    private VendorProfile vendor;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private boolean available;

    // Nutritional Info
    private Integer calories;
    private boolean isVegetarian;
    private boolean isVegan;
    private boolean isGlutenFree;
    private boolean isSpicy;

    private Integer preparationTimeMinutes = 20;

    @OneToMany(mappedBy = "product")
    private List<Review> reviews;

    @OneToMany(mappedBy = "product")
    private List<OrderLine> orderLines;
}
```

---

## API Endpoints

### Base URL
```
http://localhost:8080/api
```

### Authentication Endpoints (`/auth`)

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/auth/register/customer` | Public | Register customer account |
| POST | `/auth/register/vendor` | Public | Register vendor account |
| POST | `/auth/register/admin` | Admin | Register admin account |
| POST | `/auth/login` | Public | Login and get JWT tokens |
| POST | `/auth/refresh-token` | Public | Refresh expired JWT |
| POST | `/auth/verify-email` | Public | Verify email with token |
| POST | `/auth/resend-verification` | Public | Resend verification email |
| POST | `/auth/forgot-password` | Public | Initiate password reset |
| POST | `/auth/reset-password` | Public | Complete password reset |
| POST | `/auth/logout` | Authenticated | Logout and revoke tokens |

#### Example: Customer Registration
```json
POST /auth/register/customer
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "password": "SecurePass123!",
  "phone": "123-456-7890",
  "defaultDeliveryInstructions": "Ring doorbell",
  "address": {
    "addressLine": "123 Main St",
    "city": "Calgary",
    "province": "AB",
    "postalCode": "T2P 1A1"
  }
}
```

#### Example: Login
```json
POST /auth/login
{
  "email": "john@example.com",
  "password": "SecurePass123!"
}

Response:
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000,
  "user": {
    "publicUserId": "CUS-abc123",
    "email": "john@example.com",
    "firstName": "John",
    "role": "CUSTOMER"
  }
}
```

### Product Endpoints (`/products`, `/vendor/products`)

#### Public Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/products` | Get all available products |
| GET | `/products/{publicProductId}` | Get product details |
| GET | `/products/vendor/{publicVendorId}` | Get vendor's products |
| GET | `/products/category/{categoryId}` | Get products by category |
| GET | `/products/search?query=jollof` | Search products |
| GET | `/products/filter/price?min=5&max=20` | Filter by price |
| GET | `/products/filter/vegetarian` | Get vegetarian products |
| GET | `/products/popular` | Get top-rated products |

#### Vendor Endpoints (Requires VENDOR role)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/vendor/products` | Create new product |
| GET | `/vendor/products` | Get my products |
| PUT | `/vendor/products/{id}` | Update product |
| DELETE | `/vendor/products/{id}` | Delete product |
| PATCH | `/vendor/products/{id}/availability` | Toggle availability |
| POST | `/vendor/products/{id}/image` | Upload product image |

#### Example: Create Product
```json
POST /vendor/products
Authorization: Bearer {token}

{
  "name": "Jollof Rice",
  "description": "Traditional West African jollof rice",
  "price": 15.99,
  "categoryId": 2,
  "available": true,
  "calories": 450,
  "isVegetarian": true,
  "isVegan": false,
  "isGlutenFree": true,
  "preparationTimeMinutes": 25
}
```

### Order Endpoints

#### Customer Endpoints (`/customer/orders`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/customer/orders` | Place new order |
| GET | `/customer/orders` | Get all my orders |
| GET | `/customer/orders/active` | Get active orders |
| GET | `/customer/orders/{orderId}` | Get order details |
| PUT | `/customer/orders/{orderId}/cancel` | Cancel order |

#### Vendor Endpoints (`/vendor/orders`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/vendor/orders` | Get my received orders |
| GET | `/vendor/orders/{orderId}` | Get order details |
| PUT | `/vendor/orders/{orderId}/status` | Update order status |
| GET | `/vendor/orders/stats/summary` | Get order statistics |

#### Example: Place Order
```json
POST /customer/orders
Authorization: Bearer {customer-token}

{
  "vendorId": "VEN-xyz789",
  "deliveryAddressId": 1,
  "specialInstructions": "Extra spicy",
  "orderLines": [
    {
      "productId": "PROD-VEN-001",
      "quantity": 2,
      "specialInstructions": "No onions"
    },
    {
      "productId": "PROD-VEN-002",
      "quantity": 1
    }
  ]
}
```

### Review Endpoints (`/reviews`)

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/reviews/product/{productId}` | Public | Get product reviews |
| GET | `/reviews/vendor/{vendorId}` | Public | Get vendor reviews |
| POST | `/reviews` | Customer | Submit review |
| PUT | `/reviews/{reviewId}` | Customer | Update my review |
| DELETE | `/reviews/{reviewId}` | Customer/Admin | Delete review |

#### Example: Submit Review
```json
POST /reviews
Authorization: Bearer {customer-token}

{
  "vendorId": "VEN-xyz789",
  "productId": "PROD-VEN-001",  // Optional
  "orderId": "ORD-123456",      // Optional
  "rating": 5,
  "comment": "Excellent food, fast delivery!"
}
```

### Admin Endpoints (`/admin`)

#### User Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/users` | List all users |
| GET | `/admin/users/{id}` | Get user details |
| PUT | `/admin/users/{id}/status` | Suspend/activate user |
| DELETE | `/admin/users/{id}` | Delete user |

#### Vendor Verification
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/vendors/pending` | Get pending verifications |
| PUT | `/admin/vendors/{id}/verify` | Approve vendor |
| PUT | `/admin/vendors/{id}/reject` | Reject vendor |

---

## Security & Authentication

### JWT Token System

#### Token Structure
```
Header:
{
  "alg": "HS512",
  "typ": "JWT"
}

Payload (Encrypted with AES-256-GCM):
{
  "sub": "john@example.com",
  "publicUserId": "CUS-abc123",
  "email": "john@example.com",
  "role": "CUSTOMER",
  "iat": 1640000000,
  "exp": 1640003600
}

Signature: HMACSHA512(base64UrlEncode(header) + "." + base64UrlEncode(payload), secret)
```

#### Token Configuration
```properties
app.jwt.secret=${APP_JWT_SECRET}                  # Base64-encoded secret (min 256 bits)
app.jwt.expiration=3600000                        # 1 hour
app.jwt.refresh-expiration=604800000              # 7 days
app.jwt.encryption.enabled=true
app.jwt.encryption.key=${APP_JWT_ENCRYPTION_KEY}  # Base64-encoded AES-256 key
```

### Authentication Flow

1. **Registration**
   ```
   Client → POST /auth/register/customer → Server
   Server → Create User → Create Profile → Generate Verification Token
   Server → Send Verification Email → Return 201 Created
   ```

2. **Email Verification**
   ```
   Client → Click Email Link → POST /auth/verify-email?token=xxx
   Server → Validate Token → Mark Email as Verified → Return Success
   ```

3. **Login**
   ```
   Client → POST /auth/login → Server
   Server → Rate Limit Check → Account Lockout Check → Verify Email
   Server → Authenticate Credentials → Generate JWT Tokens
   Server → Set Refresh Token in Cookie → Return Access Token
   ```

4. **Token Refresh**
   ```
   Client → POST /auth/refresh-token (with refresh token cookie)
   Server → Validate Refresh Token → Check Revocation
   Server → Generate New Access Token → Rotate Refresh Token → Return New Tokens
   ```

### Security Features

#### 1. Account Lockout
```java
// Configuration
security.login.max-attempts=5
security.login.lockout-duration-minutes=15

// Logic
- Tracks failed login attempts per email + IP
- After 5 failed attempts: Account locked for 15 minutes
- Successful login: Reset failed attempt counter
```

#### 2. Rate Limiting
```java
// Per-IP Rate Limiting
@RateLimited(maxRequests = 10, windowSeconds = 60)
public LoginResponse login(LoginRequest request) { ... }

// Per-Email Rate Limiting
rateLimitService.checkLimit(email, 5, 300); // 5 requests per 5 minutes
```

#### 3. Password Policy
```java
- Minimum length: 8 characters
- Must contain: uppercase, lowercase, digit, special character
- Common password blacklist (top 1000 passwords)
- Password history: Cannot reuse last 3 passwords
```

#### 4. Security Events Logging
```java
// Event Types
LOGIN_SUCCESS, LOGIN_FAILED, PASSWORD_CHANGED, PASSWORD_RESET,
ACCOUNT_LOCKED, ACCOUNT_UNLOCKED, SUSPICIOUS_ACTIVITY,
TOKEN_REFRESH, LOGOUT, EMAIL_VERIFIED, EMAIL_VERIFICATION_SENT,
PROFILE_UPDATED, ROLE_CHANGED

// Example
securityEventService.logEvent(
    SecurityEventType.LOGIN_SUCCESS,
    user,
    "User logged in successfully",
    ipAddress,
    userAgent
);
```

### Authorization

#### Role-Based Access Control (RBAC)
```java
@PreAuthorize("hasRole('CUSTOMER')")
public OrderResponseDto createOrder(...) { ... }

@PreAuthorize("hasRole('VENDOR')")
public ProductResponseDto createProduct(...) { ... }

@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(...) { ... }
```

#### URL-Based Security
```java
// SecurityConfig.java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/**", "/products/**").permitAll()
    .requestMatchers("/customer/**").hasRole("CUSTOMER")
    .requestMatchers("/vendor/**").hasRole("VENDOR")
    .requestMatchers("/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
);
```

---

## Business Services

### OrderService

**Key Methods:**
```java
// Create order
OrderResponseDto createOrder(Long customerId, OrderRequestDto request)
  → Validate vendor availability
  → Validate products
  → Create order lines (with price snapshot)
  → Calculate financials (subtotal, tax, total)
  → Create payment record
  → Send notifications

// Update order status
void updateOrderStatus(Long orderId, OrderStatus newStatus)
  → Validate status transition
  → Update timestamps
  → Notify customer & vendor

// Cancel order
void cancelOrder(Long orderId)
  → Check if cancellable (PENDING or CONFIRMED only)
  → Update status to CANCELLED
  → Initiate refund if payment completed
  → Notify customer & vendor
```

**Order Status Flow:**
```
PENDING → CONFIRMED → PREPARING → READY_FOR_PICKUP → OUT_FOR_DELIVERY → DELIVERED

Alternative paths:
PENDING → CANCELLED
CONFIRMED → CANCELLED
DELIVERED → REFUNDED (via admin)
```

**Financial Calculation:**
```java
@PrePersist
@PreUpdate
private void calculateFinancials() {
    this.subtotal = calculateSubtotal();     // Sum of order lines
    this.tax = subtotal.multiply(TAX_RATE);  // 5% tax
    this.totalAmount = subtotal
        .add(deliveryFee)
        .add(tax)
        .subtract(discount);
}
```

### PaymentService

**Payment Flow:**
```java
1. Create payment record (status = PENDING)
2. Call payment gateway (Stripe/PayPal integration)
3. If successful:
   - Update status to COMPLETED
   - Update order status to CONFIRMED
   - Send payment success notification
4. If failed:
   - Update status to FAILED
   - Update order status remains PENDING
   - Send payment failed notification
```

**Refund Process:**
```java
void refundPayment(Long paymentId)
  → Validate payment is COMPLETED
  → Call payment gateway refund API
  → Update status to REFUNDED
  → Update order status to REFUNDED
  → Send refund confirmation email
```

### NotificationService

**Notification Types:**
```java
ORDER_UPDATE        → "Your order status changed to: PREPARING"
DELIVERY_UPDATE     → "Driver is 5 minutes away"
PAYMENT_SUCCESS     → "Payment of $25.99 received"
PAYMENT_FAILED      → "Payment failed, please try again"
NEW_ORDER           → "New order received: ORD-123456"
REVIEW_RECEIVED     → "You received a 5-star review!"
PROMO               → "20% off this weekend!"
SYSTEM_ALERT        → "Scheduled maintenance tonight"
```

**Notification Channels:**
```java
- In-app notifications (stored in database)
- Email notifications (via SMTP)
- SMS notifications (future: Twilio integration)
- Push notifications (future: Firebase integration)
```

### VendorService

**Operating Hours Management:**
```java
// Set operating hours
{
  "MONDAY": { "open": "09:00", "close": "21:00" },
  "TUESDAY": { "open": "09:00", "close": "21:00" },
  "WEDNESDAY": { "open": "09:00", "close": "21:00" },
  "THURSDAY": { "open": "09:00", "close": "22:00" },
  "FRIDAY": { "open": "09:00", "close": "23:00" },
  "SATURDAY": { "open": "10:00", "close": "23:00" },
  "SUNDAY": { "open": "10:00", "close": "20:00" }
}

// Check if vendor is open now
boolean isOpenNow() {
  DayOfWeek today = LocalDate.now().getDayOfWeek();
  DayHours hours = operatingHours.get(today.name());
  LocalTime now = LocalTime.now();
  return now.isAfter(hours.open) && now.isBefore(hours.close);
}
```

---

## Database Schema

### Core Tables

#### users
```sql
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_user_id VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    profile_image_url VARCHAR(500),
    role ENUM('CUSTOMER', 'VENDOR', 'ADMIN') NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    email_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_email (email),
    INDEX idx_public_user_id (public_user_id),
    INDEX idx_role (role)
);
```

#### vendor_profile
```sql
CREATE TABLE vendor_profile (
    vendor_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_vendor_id VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT UNIQUE NOT NULL,
    restaurant_name VARCHAR(255) NOT NULL,
    description TEXT,
    cuisine_type VARCHAR(100),
    logo_url VARCHAR(500),
    banner_url VARCHAR(500),
    tax_id VARCHAR(50),
    business_license_url VARCHAR(500),

    offers_delivery BOOLEAN DEFAULT TRUE,
    offers_pickup BOOLEAN DEFAULT TRUE,
    delivery_fee DECIMAL(10,2) DEFAULT 0.00,
    minimum_order_amount DECIMAL(10,2) DEFAULT 0.00,
    estimated_delivery_minutes INT DEFAULT 30,
    preparation_time INT DEFAULT 20,

    operating_hours_json TEXT,

    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    verified_at TIMESTAMP,

    total_orders_completed BIGINT DEFAULT 0,
    total_revenue DECIMAL(15,2) DEFAULT 0.00,

    address_id BIGINT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (address_id) REFERENCES address(address_id),

    INDEX idx_public_vendor_id (public_vendor_id),
    INDEX idx_is_verified (is_verified),
    INDEX idx_is_active (is_active)
);
```

#### orders
```sql
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_order_id VARCHAR(50) UNIQUE NOT NULL,
    customer_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,
    delivery_address_id BIGINT NOT NULL,

    subtotal DECIMAL(10,2) NOT NULL,
    delivery_fee DECIMAL(10,2) DEFAULT 0.00,
    tax DECIMAL(10,2) DEFAULT 0.00,
    discount DECIMAL(10,2) DEFAULT 0.00,
    total_amount DECIMAL(10,2) NOT NULL,

    status ENUM('PENDING', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP',
                'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'REFUNDED') NOT NULL,

    special_instructions TEXT,

    order_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    preparing_at TIMESTAMP,
    ready_at TIMESTAMP,
    out_for_delivery_at TIMESTAMP,
    delivered_at TIMESTAMP,
    cancelled_at TIMESTAMP,

    FOREIGN KEY (customer_id) REFERENCES customer_profile(customer_id),
    FOREIGN KEY (vendor_id) REFERENCES vendor_profile(vendor_id),
    FOREIGN KEY (delivery_address_id) REFERENCES address(address_id),

    INDEX idx_customer_id (customer_id),
    INDEX idx_vendor_id (vendor_id),
    INDEX idx_status (status),
    INDEX idx_order_time (order_time)
);
```

### Database Migrations (Flyway)

Migration files located in: `src/main/resources/db/migration/`

```
V1__Create_users_table.sql
V2__Create_profiles_and_addresses.sql
V3__Create_products_and_categories.sql
V4__Create_orders_and_payments.sql
V5__Create_reviews_and_notifications.sql
V6__Create_security_tables.sql
```

---

## Configuration

### Environment Variables (.env)

```bash
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=afrochow
DB_USERNAME=root
DB_PASSWORD=your_password

# JWT
APP_JWT_SECRET=base64_encoded_secret_min_256_bits
APP_JWT_EXPIRATION=3600000
APP_JWT_REFRESH_EXPIRATION=604800000
APP_JWT_ENCRYPTION_KEY=base64_encoded_aes_256_key
APP_TOKEN_SECRET=your_token_secret

# Security
SECURITY_MAX_LOGIN_ATTEMPTS=5
SECURITY_LOCKOUT_DURATION=15
SECURITY_PASSWORD_RESET_EXPIRATION=60

# Email (Gmail)
DEV_GMAIL_USERNAME=your-email@gmail.com
DEV_GMAIL_PASSWORD=your-app-specific-password

# Application
APP_NAME=Afrochow
APP_URL=https://afrochow.ca
APP_FRONTEND_URL=http://localhost:3000

# File Upload
APP_UPLOAD_MAX_FILE_SIZE=10485760  # 10MB in bytes
```

### Application Properties

**Key Configurations:**
```properties
# Server
server.port=8080
server.servlet.context-path=/api

# Database Pool
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000

# JPA
spring.jpa.hibernate.ddl-auto=create
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# CORS
cors.allowed-origins=http://localhost:3000,http://localhost:4200

# File Upload
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Cache
spring.cache.type=caffeine
```

---

## Development Guide

### Prerequisites

- Java 21 (JDK 21)
- MySQL 8.0+
- Maven 3.9+
- IDE (IntelliJ IDEA recommended)

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Afrochow-Backend
   ```

2. **Configure environment**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Create database**
   ```sql
   CREATE DATABASE afrochow;
   ```

4. **Run migrations**
   ```bash
   ./mvnw flyway:migrate
   ```

5. **Build the project**
   ```bash
   ./mvnw clean install
   ```

6. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

7. **Access Swagger UI**
   ```
   http://localhost:8080/api/swagger-ui.html
   ```

### Testing

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=OrderServiceTest

# Run with coverage
./mvnw test jacoco:report
```

### Database Seeding

The project includes a comprehensive seeder at `src/main/java/com/afrochow/seeder/CompleteFinalSeeder.java`

```bash
# Run seeder (creates sample data)
# Option 1: Uncomment @Component annotation in CompleteFinalSeeder.java
# Option 2: Call seeder endpoint (if exposed)
POST /api/admin/seed
```

### Common Development Tasks

#### Generate JWT Secret
```bash
# Generate 256-bit secret
openssl rand -base64 32
```

#### Generate AES Encryption Key
```bash
# Generate 256-bit AES key
openssl rand -base64 32
```

#### Create New Migration
```sql
-- File: src/main/resources/db/migration/V7__Your_description.sql
-- Add your SQL here
```

#### Add New Endpoint
```java
// 1. Create DTO
public record NewFeatureRequestDto(String field) {}

// 2. Add service method
@Service
public class NewFeatureService {
    public NewFeatureResponseDto create(NewFeatureRequestDto request) {
        // Business logic
    }
}

// 3. Add controller
@RestController
@RequestMapping("/new-feature")
public class NewFeatureController {
    @PostMapping
    public ResponseEntity<NewFeatureResponseDto> create(@RequestBody NewFeatureRequestDto request) {
        return ResponseEntity.ok(service.create(request));
    }
}
```

### API Testing Script

The project includes a Bash script for API testing: `test-api.sh`

```bash
# Run API tests
./test-api.sh
```

---

## Performance & Optimization

### Caching Strategy

```java
@Cacheable(value = "products", key = "#publicProductId")
public ProductResponseDto getProduct(String publicProductId) {
    // Cached for 5 minutes
}

@CacheEvict(value = "products", key = "#productId")
public void updateProduct(String productId, ProductUpdateDto dto) {
    // Evicts cache on update
}
```

### Query Optimization

```java
// Use projections for list endpoints
@Query("SELECT new com.afrochow.product.dto.ProductSummaryDto(p.publicProductId, p.name, p.price) FROM Product p")
List<ProductSummaryDto> findAllSummaries();

// Fetch joins to avoid N+1 queries
@Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderLines WHERE o.orderId = :orderId")
Order findByIdWithLines(@Param("orderId") Long orderId);
```

### Database Indexing

```sql
-- Critical indexes for performance
CREATE INDEX idx_orders_customer_status ON orders(customer_id, status);
CREATE INDEX idx_products_vendor_available ON products(vendor_id, available);
CREATE INDEX idx_reviews_product_visible ON reviews(product_id, is_visible);
```

---

## Deployment

### Production Checklist

- [ ] Set `spring.jpa.hibernate.ddl-auto=validate`
- [ ] Enable HTTPS
- [ ] Configure production database credentials
- [ ] Set strong JWT secrets (min 256 bits)
- [ ] Enable Flyway migrations
- [ ] Configure production CORS origins
- [ ] Set up monitoring (Spring Actuator + Prometheus)
- [ ] Configure logging (log aggregation)
- [ ] Set up backup strategy
- [ ] Enable rate limiting
- [ ] Configure CDN for static assets
- [ ] Set up SSL certificates
- [ ] Configure firewall rules
- [ ] Enable database connection pooling
- [ ] Set up health checks
- [ ] Configure auto-scaling

### Docker Deployment

```dockerfile
# Dockerfile
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/afrochow-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```yaml
# docker-compose.yml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=mysql
    depends_on:
      - mysql

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_DATABASE=afrochow
      - MYSQL_ROOT_PASSWORD=${DB_PASSWORD}
    volumes:
      - mysql_data:/var/lib/mysql

volumes:
  mysql_data:
```

---

## Troubleshooting

### Common Issues

**Issue: Cannot connect to database**
```
Solution:
1. Check MySQL is running: sudo systemctl status mysql
2. Verify credentials in .env file
3. Check database exists: SHOW DATABASES;
```

**Issue: JWT token invalid**
```
Solution:
1. Check token expiration
2. Verify JWT secret matches in .env
3. Clear refresh tokens: DELETE FROM refresh_token;
```

**Issue: Email verification not working**
```
Solution:
1. Check SMTP credentials in .env
2. Verify email server connection
3. Check spam folder
4. Review application logs for errors
```

---

## License

Copyright © 2026 Afrochow. All rights reserved.

---

## Contact & Support

For technical support or questions:
- Email: support@afrochow.ca
- GitHub Issues: [Link to issues]
- Documentation: [Link to docs]
