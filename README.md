# 🍔 Food Delivery Application (AFROCHOW) - Backend API

## 📝 Description

This is a robust backend API for a food delivery platform, developed using Spring Boot. It offers comprehensive endpoints for managing users, vendors, customers, products, orders, and payments. The API supports full CRUD operations, secure JWT authentication, media uploads, multi-channel notifications, and advanced search and filtering options to facilitate efficient data retrieval and management.

---

## 🚀 Features

<pre>
👤 User Management
- User registration, update, and deletion
- Email verification and password reset flows
- Role-based access control (CUSTOMER, VENDOR, ADMIN)
- BCrypt password hashing

🧑‍💼 Vendor Management
- Vendor registration and profile management
- Product and store management
- Order handling and delivery coordination

🛍️ Customer Management
- Customer profile and address management
- Order placement and tracking
- Product reviews and ratings
- Favourite products and vendors

📦 Product Management
- Add, update, and delete products
- Category and inventory management
- Search and filter products by name, category, or store
- Upload and associate product images

🛒 Order Management
- Full order lifecycle (placement → preparation → delivery)
- Role-scoped views for customers, vendors, and admins
- Order line item tracking

💳 Payment Management
- Payment processing and confirmation
- Refund handling
- Payment status tracking

🔔 Notification System
- In-app notifications (database-backed)
- Email notifications via Thymeleaf HTML templates
- Push and SMS channels (extensible stubs)

🖼️ File Upload
- Product image uploads
- Vendor and customer profile picture uploads
- Type-checked file handling

🔒 Security
- JWT authentication with encrypted payloads and refresh token rotation
- Account lockout after repeated failed login attempts
- Comprehensive security headers (HSTS, CSP, X-Frame-Options)
- Input validation for all endpoints
- HTTP status-based error handling
</pre>

---

## 🔧 Technologies Used

- Java 21
- Spring Boot 4.0
- Spring Security 7
- Spring Data JPA / Hibernate
- MySQL 8.0+
- Flyway for database migrations
- JWT (JJWT 0.12.3) for authentication
- Caffeine for caching
- SpringDoc OpenAPI (Swagger UI)
- Thymeleaf for email templating
- Lombok for boilerplate reduction
- Maven for dependency management

---

## 📂 Project Structure

<pre>
src/
├── main/
│   ├── java/
│   │   └── com/afrochow/
│   │       ├── address/          # Customer delivery addresses
│   │       ├── admin/            # Admin user management & profiles
│   │       ├── analytics/        # Analytics DTOs
│   │       ├── auth/             # Registration, login, token management
│   │       ├── category/         # Product categories
│   │       ├── common/           # Shared enums, exceptions, ApiResponse
│   │       ├── config/           # App configuration classes
│   │       ├── customer/         # Customer profiles & operations
│   │       ├── email/            # Email service
│   │       ├── favorite/         # Favourited products & vendors
│   │       ├── image/            # Image upload & storage
│   │       ├── notification/     # Multi-channel notification orchestration
│   │       ├── order/            # Order management
│   │       ├── orderline/        # Order line items
│   │       ├── payment/          # Payment processing & refunds
│   │       ├── product/          # Product catalogue
│   │       ├── review/           # Reviews & ratings
│   │       ├── search/           # Product & vendor search
│   │       ├── security/         # JWT filter, login attempts, security events
│   │       ├── seeder/           # Database seeding
│   │       ├── stats/            # Platform statistics
│   │       ├── user/             # Core user model & repository
│   │       └── vendor/           # Vendor profiles & operations
│   └── resources/
│       ├── db/migration/         # Flyway SQL migrations (V1–V6)
│       ├── templates/email/      # HTML email templates
│       ├── application.properties
│       └── application-prod.properties
</pre>

---

## 🛠️ Installation & Setup

### Prerequisites

- Java 21 JDK
- Maven 3.8+
- MySQL 8.0+

### Clone the repository

```bash
git clone https://github.com/yourusername/Afrochow-Backend.git
cd Afrochow-Backend
```

### Configure the database

1. Create a MySQL database:

```sql
CREATE DATABASE afrochow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. Copy the environment file and update with your credentials:

```bash
cp .env.example .env
```

3. Set the required variables in `.env`:

```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=afrochow
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password

APP_JWT_SECRET=your_jwt_secret
APP_JWT_ENCRYPTION_KEY=your_encryption_key
APP_TOKEN_SECRET=your_token_secret
```

### Build and run

```bash
mvn clean install
mvn spring-boot:run
```

The API will be available at `http://localhost:8080/api`.
Swagger UI (dev only): `http://localhost:8080/swagger-ui.html`

---

## 🌐 API Endpoints

<pre>
🔑 Auth Endpoints
POST    /api/auth/register                      - Register a new user
POST    /api/auth/login                         - Login and receive tokens
POST    /api/auth/refresh                       - Refresh access token
POST    /api/auth/verify-email                  - Verify email address
POST    /api/auth/forgot-password               - Request password reset
POST    /api/auth/reset-password                - Reset password with token

👤 Customer Endpoints
GET     /api/customer/profile                   - Get customer profile
PUT     /api/customer/profile                   - Update customer profile
GET     /api/customer/addresses                 - Get saved addresses
POST    /api/customer/addresses                 - Add a new address
DELETE  /api/customer/addresses/{id}            - Delete an address

🧑‍💼 Vendor Endpoints
GET     /api/vendor/profile                     - Get vendor profile
PUT     /api/vendor/profile                     - Update vendor profile
GET     /api/vendor/orders                      - Get vendor orders
PUT     /api/vendor/orders/{id}                 - Update order status

📦 Product Endpoints
POST    /api/product                            - Create a new product
PUT     /api/product/{id}                       - Update product details
GET     /api/product                            - Get all products
GET     /api/product/{id}                       - Get product by ID
GET     /api/product/search?name=...            - Search products by name
DELETE  /api/product/{id}                       - Delete a product
POST    /api/product/{id}/image                 - Upload a product image

🏷️ Category Endpoints
GET     /api/category                           - Get all categories
POST    /api/category                           - Create a category

🛒 Order Endpoints
POST    /api/orders                             - Place a new order
GET     /api/orders                             - Get customer orders
GET     /api/orders/{id}                        - Get order by ID
PUT     /api/orders/{id}/cancel                 - Cancel an order

💳 Payment Endpoints
POST    /api/payments                           - Process a payment
GET     /api/payments/{id}                      - Get payment details
POST    /api/payments/{id}/refund               - Request a refund

⭐ Review Endpoints
POST    /api/reviews                            - Submit a review
GET     /api/reviews/product/{id}               - Get reviews for a product
GET     /api/reviews/vendor/{id}                - Get reviews for a vendor

🔔 Notification Endpoints
GET     /api/notifications                      - Get user notifications
PUT     /api/notifications/{id}/read            - Mark notification as read

🔍 Search Endpoints
GET     /api/search?q=...                       - Search products and vendors

📊 Admin Endpoints
GET     /api/admin/users                        - Get all users
DELETE  /api/admin/users/{id}                   - Delete a user
GET     /api/admin/stats                        - Get platform statistics
</pre>

---

## 🔒 Security

- JWT authentication with encrypted payloads
- Refresh token rotation
- BCrypt password hashing (configurable cost factor)
- Account lockout after 5 failed login attempts (15-minute lockout)
- Email verification on registration
- HSTS, CSP, X-Frame-Options, and Referrer-Policy headers
- CORS configuration per environment
- Input validation for all endpoints
- Proper error handling and HTTP status codes

---

## 🤝 Contributing

Contributions are welcome!
Please fork the repository and create a pull request with your changes.

---

## 📧 Contact

For questions or support, reach out via email: ibikunleogunbanwo@gmail.com
