# Afrochow API Quick Reference

> **Base URL**: `http://localhost:8080/api`
> **API Version**: v1
> **Authentication**: Bearer Token (JWT)

---

## Authentication

### Register Customer
```http
POST /auth/register/customer
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "password": "SecurePass123!",
  "phone": "403-123-4567",
  "defaultDeliveryInstructions": "Ring doorbell",
  "address": {
    "addressLine": "123 Main St, Apt 4B",
    "city": "Calgary",
    "province": "AB",
    "postalCode": "T2P 1A1"
  }
}
```

### Register Vendor
```http
POST /auth/register/vendor
Content-Type: application/json

{
  "firstName": "Jane",
  "lastName": "Smith",
  "email": "jane@restaurant.com",
  "password": "SecurePass123!",
  "phone": "403-555-1234",
  "restaurantName": "Mama's African Kitchen",
  "description": "Authentic West African cuisine",
  "cuisineType": "West African",
  "taxId": "123456789",
  "offersDelivery": true,
  "offersPickup": true,
  "deliveryFee": 5.00,
  "minimumOrderAmount": 15.00,
  "estimatedDeliveryMinutes": 45,
  "address": {
    "addressLine": "456 Restaurant Ave",
    "city": "Calgary",
    "province": "AB",
    "postalCode": "T2N 4N1"
  },
  "operatingHours": {
    "MONDAY": { "open": "09:00", "close": "21:00" },
    "TUESDAY": { "open": "09:00", "close": "21:00" },
    "WEDNESDAY": { "open": "09:00", "close": "21:00" },
    "THURSDAY": { "open": "09:00", "close": "22:00" },
    "FRIDAY": { "open": "09:00", "close": "23:00" },
    "SATURDAY": { "open": "10:00", "close": "23:00" },
    "SUNDAY": { "open": "10:00", "close": "20:00" }
  }
}
```

### Login
```http
POST /auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePass123!"
}

Response 200 OK:
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000,
  "user": {
    "publicUserId": "CUS-abc123",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "role": "CUSTOMER"
  }
}
```

### Refresh Token
```http
POST /auth/refresh-token
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}

Response 200 OK:
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "expiresIn": 3600000
}
```

### Verify Email
```http
POST /auth/verify-email?token={verification-token}
```

### Forgot Password
```http
POST /auth/forgot-password
Content-Type: application/json

{
  "email": "john@example.com"
}
```

### Reset Password
```http
POST /auth/reset-password
Content-Type: application/json

{
  "token": "reset-token-from-email",
  "newPassword": "NewSecurePass123!"
}
```

### Logout
```http
POST /auth/logout
Authorization: Bearer {access-token}
```

---

## Products

### Get All Products (Public)
```http
GET /products
```

### Get Product Details (Public)
```http
GET /products/{publicProductId}

Response 200 OK:
{
  "publicProductId": "PROD-VEN-001",
  "name": "Jollof Rice",
  "description": "Traditional West African jollof rice",
  "price": 15.99,
  "imageUrl": "/images/products/jollof.jpg",
  "available": true,
  "calories": 450,
  "isVegetarian": true,
  "isVegan": false,
  "isGlutenFree": true,
  "isSpicy": true,
  "preparationTimeMinutes": 25,
  "averageRating": 4.8,
  "totalReviews": 32,
  "vendor": {
    "publicVendorId": "VEN-xyz789",
    "restaurantName": "Mama's African Kitchen"
  },
  "category": {
    "categoryId": 2,
    "name": "Main Dishes"
  }
}
```

### Get Products by Vendor (Public)
```http
GET /products/vendor/{publicVendorId}
```

### Get Products by Category (Public)
```http
GET /products/category/{categoryId}
```

### Search Products (Public)
```http
GET /products/search?query=jollof
```

### Filter Products by Price (Public)
```http
GET /products/filter/price?min=5&max=20
```

### Get Vegetarian Products (Public)
```http
GET /products/filter/vegetarian
```

### Get Popular Products (Public)
```http
GET /products/popular
```

### Create Product (Vendor)
```http
POST /vendor/products
Authorization: Bearer {vendor-token}
Content-Type: application/json

{
  "name": "Egusi Soup",
  "description": "Nigerian melon seed soup",
  "price": 18.99,
  "categoryId": 3,
  "available": true,
  "calories": 520,
  "isVegetarian": false,
  "isVegan": false,
  "isGlutenFree": true,
  "isSpicy": true,
  "preparationTimeMinutes": 30
}

Response 201 Created:
{
  "publicProductId": "PROD-VEN-002",
  "name": "Egusi Soup",
  "price": 18.99,
  ...
}
```

### Update Product (Vendor)
```http
PUT /vendor/products/{publicProductId}
Authorization: Bearer {vendor-token}
Content-Type: application/json

{
  "name": "Egusi Soup (Updated)",
  "price": 19.99,
  "available": true
}
```

### Toggle Product Availability (Vendor)
```http
PATCH /vendor/products/{publicProductId}/availability
Authorization: Bearer {vendor-token}

Response 200 OK:
{
  "publicProductId": "PROD-VEN-002",
  "available": false
}
```

### Upload Product Image (Vendor)
```http
POST /vendor/products/{publicProductId}/image
Authorization: Bearer {vendor-token}
Content-Type: multipart/form-data

file: [binary image data]
```

### Delete Product (Vendor)
```http
DELETE /vendor/products/{publicProductId}
Authorization: Bearer {vendor-token}

Response 204 No Content
```

---

## Orders

### Place Order (Customer)
```http
POST /customer/orders
Authorization: Bearer {customer-token}
Content-Type: application/json

{
  "vendorId": "VEN-xyz789",
  "deliveryAddressId": 1,
  "specialInstructions": "Extra spicy, no onions",
  "orderLines": [
    {
      "productId": "PROD-VEN-001",
      "quantity": 2,
      "specialInstructions": "Extra sauce"
    },
    {
      "productId": "PROD-VEN-002",
      "quantity": 1
    }
  ]
}

Response 201 Created:
{
  "publicOrderId": "ORD-123456",
  "customer": {
    "publicCustomerId": "CUS-abc123",
    "firstName": "John",
    "lastName": "Doe"
  },
  "vendor": {
    "publicVendorId": "VEN-xyz789",
    "restaurantName": "Mama's African Kitchen"
  },
  "orderLines": [
    {
      "productNameAtPurchase": "Jollof Rice",
      "quantity": 2,
      "priceAtPurchase": 15.99,
      "lineTotal": 31.98
    },
    {
      "productNameAtPurchase": "Egusi Soup",
      "quantity": 1,
      "priceAtPurchase": 18.99,
      "lineTotal": 18.99
    }
  ],
  "subtotal": 50.97,
  "deliveryFee": 5.00,
  "tax": 2.55,
  "totalAmount": 58.52,
  "status": "PENDING",
  "orderTime": "2026-01-25T14:30:00",
  "estimatedDeliveryTime": "2026-01-25T15:15:00"
}
```

### Get My Orders (Customer)
```http
GET /customer/orders
Authorization: Bearer {customer-token}
```

### Get Active Orders (Customer)
```http
GET /customer/orders/active
Authorization: Bearer {customer-token}
```

### Get Order Details (Customer)
```http
GET /customer/orders/{publicOrderId}
Authorization: Bearer {customer-token}
```

### Cancel Order (Customer)
```http
PUT /customer/orders/{publicOrderId}/cancel
Authorization: Bearer {customer-token}

Response 200 OK:
{
  "publicOrderId": "ORD-123456",
  "status": "CANCELLED",
  "cancelledAt": "2026-01-25T14:35:00"
}
```

### Get Vendor Orders (Vendor)
```http
GET /vendor/orders
Authorization: Bearer {vendor-token}
```

### Update Order Status (Vendor)
```http
PUT /vendor/orders/{publicOrderId}/status
Authorization: Bearer {vendor-token}
Content-Type: application/json

{
  "status": "PREPARING"
}

Valid status transitions:
PENDING → CONFIRMED
CONFIRMED → PREPARING
PREPARING → READY_FOR_PICKUP
READY_FOR_PICKUP → OUT_FOR_DELIVERY
OUT_FOR_DELIVERY → DELIVERED
```

### Get Order Statistics (Vendor)
```http
GET /vendor/orders/stats/summary
Authorization: Bearer {vendor-token}

Response 200 OK:
{
  "totalOrders": 156,
  "completedOrders": 142,
  "activeOrders": 8,
  "cancelledOrders": 6,
  "totalRevenue": 4521.75,
  "averageOrderValue": 29.00
}
```

---

## Reviews

### Submit Review (Customer)
```http
POST /reviews
Authorization: Bearer {customer-token}
Content-Type: application/json

{
  "vendorId": "VEN-xyz789",
  "productId": "PROD-VEN-001",
  "orderId": "ORD-123456",
  "rating": 5,
  "comment": "Absolutely delicious! Best jollof rice in Calgary."
}

Response 201 Created:
{
  "reviewId": 1,
  "rating": 5,
  "comment": "Absolutely delicious! Best jollof rice in Calgary.",
  "createdAt": "2026-01-25T16:00:00",
  "user": {
    "firstName": "John",
    "lastName": "Doe"
  }
}
```

### Get Product Reviews (Public)
```http
GET /reviews/product/{publicProductId}

Response 200 OK:
[
  {
    "reviewId": 1,
    "rating": 5,
    "comment": "Excellent!",
    "helpfulCount": 12,
    "createdAt": "2026-01-25T16:00:00",
    "user": {
      "firstName": "John",
      "lastName": "D."
    }
  },
  ...
]
```

### Get Vendor Reviews (Public)
```http
GET /reviews/vendor/{publicVendorId}
```

### Get Average Rating (Public)
```http
GET /reviews/average/product/{publicProductId}

Response 200 OK:
{
  "averageRating": 4.8,
  "totalReviews": 32
}
```

### Update Review (Customer)
```http
PUT /reviews/{reviewId}
Authorization: Bearer {customer-token}
Content-Type: application/json

{
  "rating": 4,
  "comment": "Updated review text"
}
```

### Delete Review (Customer/Admin)
```http
DELETE /reviews/{reviewId}
Authorization: Bearer {customer-token}

Response 204 No Content
```

---

## Customer Profile

### Get My Profile
```http
GET /customer/profile
Authorization: Bearer {customer-token}

Response 200 OK:
{
  "publicCustomerId": "CUS-abc123",
  "user": {
    "publicUserId": "CUS-abc123",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "403-123-4567",
    "profileImageUrl": "/images/profiles/john.jpg"
  },
  "defaultDeliveryInstructions": "Ring doorbell",
  "paymentMethod": "CREDIT_CARD",
  "loyaltyPoints": 150,
  "addresses": [
    {
      "addressId": 1,
      "addressLine": "123 Main St, Apt 4B",
      "city": "Calgary",
      "province": "AB",
      "postalCode": "T2P 1A1",
      "isDefault": true
    }
  ]
}
```

### Update My Profile
```http
PUT /customer/profile
Authorization: Bearer {customer-token}
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "phone": "403-123-4567",
  "defaultDeliveryInstructions": "Leave at front door"
}
```

### Upload Profile Avatar
```http
POST /customer/profile/avatar
Authorization: Bearer {customer-token}
Content-Type: multipart/form-data

file: [binary image data]
```

---

## Address Management

### Get My Addresses
```http
GET /customer/addresses
Authorization: Bearer {customer-token}
```

### Add Address
```http
POST /customer/addresses
Authorization: Bearer {customer-token}
Content-Type: application/json

{
  "addressLine": "789 Oak St, Unit 12",
  "city": "Calgary",
  "province": "AB",
  "postalCode": "T3B 2M1",
  "isDefault": false
}

Response 201 Created:
{
  "publicAddressId": "ADDR-def456",
  "addressLine": "789 Oak St, Unit 12",
  "city": "Calgary",
  "province": "AB",
  "postalCode": "T3B 2M1",
  "country": "Canada",
  "isDefault": false
}
```

### Update Address
```http
PUT /customer/addresses/{addressId}
Authorization: Bearer {customer-token}
Content-Type: application/json

{
  "addressLine": "789 Oak St, Unit 15",
  "city": "Calgary",
  "province": "AB",
  "postalCode": "T3B 2M1"
}
```

### Set Default Address
```http
PATCH /customer/addresses/{addressId}/default
Authorization: Bearer {customer-token}
```

### Delete Address
```http
DELETE /customer/addresses/{addressId}
Authorization: Bearer {customer-token}

Response 204 No Content
```

---

## Vendor Profile

### Get My Vendor Profile
```http
GET /vendor/profile
Authorization: Bearer {vendor-token}

Response 200 OK:
{
  "publicVendorId": "VEN-xyz789",
  "user": {
    "publicUserId": "VEN-xyz789",
    "email": "jane@restaurant.com",
    "firstName": "Jane",
    "lastName": "Smith"
  },
  "restaurantName": "Mama's African Kitchen",
  "description": "Authentic West African cuisine",
  "cuisineType": "West African",
  "logoUrl": "/images/vendors/logo.jpg",
  "bannerUrl": "/images/vendors/banner.jpg",
  "offersDelivery": true,
  "offersPickup": true,
  "deliveryFee": 5.00,
  "minimumOrderAmount": 15.00,
  "estimatedDeliveryMinutes": 45,
  "preparationTime": 20,
  "isVerified": true,
  "isActive": true,
  "verifiedAt": "2026-01-10T10:00:00",
  "totalOrdersCompleted": 156,
  "totalRevenue": 4521.75,
  "operatingHours": {
    "MONDAY": { "open": "09:00", "close": "21:00" },
    ...
  },
  "address": {
    "addressLine": "456 Restaurant Ave",
    "city": "Calgary",
    "province": "AB",
    "postalCode": "T2N 4N1"
  }
}
```

### Update Vendor Profile
```http
PUT /vendor/profile
Authorization: Bearer {vendor-token}
Content-Type: application/json

{
  "restaurantName": "Mama's African Kitchen",
  "description": "Updated description",
  "deliveryFee": 6.00,
  "operatingHours": {
    "MONDAY": { "open": "10:00", "close": "22:00" }
  }
}
```

### Update Vendor Address
```http
PUT /vendor/profile/address
Authorization: Bearer {vendor-token}
Content-Type: application/json

{
  "addressLine": "456 Restaurant Ave, Unit 2",
  "city": "Calgary",
  "province": "AB",
  "postalCode": "T2N 4N1"
}
```

### Upload Vendor Logo/Banner
```http
POST /vendor/profile/image?type=logo
Authorization: Bearer {vendor-token}
Content-Type: multipart/form-data

file: [binary image data]

Query params:
- type: "logo" or "banner"
```

---

## Categories

### Get All Categories (Public)
```http
GET /categories

Response 200 OK:
[
  {
    "categoryId": 1,
    "name": "Appetizers",
    "description": "Starters and small plates",
    "iconUrl": "/images/categories/appetizers.svg",
    "displayOrder": 1,
    "isActive": true,
    "productCount": 45
  },
  {
    "categoryId": 2,
    "name": "Main Dishes",
    "description": "Hearty main courses",
    "iconUrl": "/images/categories/main.svg",
    "displayOrder": 2,
    "isActive": true,
    "productCount": 128
  }
]
```

### Get Category Details (Public)
```http
GET /categories/{categoryId}
```

### Get Category Products (Public)
```http
GET /categories/{categoryId}/products
```

### Create Category (Admin)
```http
POST /categories
Authorization: Bearer {admin-token}
Content-Type: application/json

{
  "name": "Desserts",
  "description": "Sweet treats and desserts",
  "displayOrder": 5,
  "isActive": true
}
```

### Update Category (Admin)
```http
PUT /categories/{categoryId}
Authorization: Bearer {admin-token}
Content-Type: application/json

{
  "name": "Desserts & Sweets",
  "description": "Updated description"
}
```

---

## Notifications

### Get My Notifications
```http
GET /notifications
Authorization: Bearer {token}

Response 200 OK:
[
  {
    "notificationId": 1,
    "title": "Order Confirmed",
    "message": "Your order ORD-123456 has been confirmed",
    "type": "ORDER_UPDATE",
    "relatedEntityType": "ORDER",
    "relatedEntityId": "ORD-123456",
    "isRead": false,
    "createdAt": "2026-01-25T14:32:00"
  }
]
```

### Get Unread Count
```http
GET /notifications/unread
Authorization: Bearer {token}

Response 200 OK:
{
  "unreadCount": 3
}
```

### Mark Notification as Read
```http
PUT /notifications/{notificationId}/read
Authorization: Bearer {token}
```

---

## Admin Endpoints

### List All Users
```http
GET /admin/users
Authorization: Bearer {admin-token}

Query params:
- page: 0
- size: 20
- role: CUSTOMER | VENDOR | ADMIN
- isActive: true | false
```

### Get User Details
```http
GET /admin/users/{publicUserId}
Authorization: Bearer {admin-token}
```

### Suspend/Activate User
```http
PUT /admin/users/{publicUserId}/status
Authorization: Bearer {admin-token}
Content-Type: application/json

{
  "isActive": false,
  "reason": "Violation of terms of service"
}
```

### Get Pending Vendor Verifications
```http
GET /admin/vendors/pending
Authorization: Bearer {admin-token}
```

### Approve Vendor
```http
PUT /admin/vendors/{publicVendorId}/verify
Authorization: Bearer {admin-token}
```

### Get Order Reports
```http
GET /admin/reports/orders
Authorization: Bearer {admin-token}

Query params:
- startDate: 2026-01-01
- endDate: 2026-01-31
```

---

## Error Responses

### 400 Bad Request
```json
{
  "timestamp": "2026-01-25T14:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "must be a valid email address"
    },
    {
      "field": "password",
      "message": "must be at least 8 characters"
    }
  ]
}
```

### 401 Unauthorized
```json
{
  "timestamp": "2026-01-25T14:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired token"
}
```

### 403 Forbidden
```json
{
  "timestamp": "2026-01-25T14:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to access this resource"
}
```

### 404 Not Found
```json
{
  "timestamp": "2026-01-25T14:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Product with ID 'PROD-VEN-999' not found"
}
```

### 429 Too Many Requests
```json
{
  "timestamp": "2026-01-25T14:30:00",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again in 60 seconds."
}
```

---

## Common HTTP Status Codes

| Code | Meaning | When Used |
|------|---------|-----------|
| 200 | OK | Successful GET, PUT, PATCH |
| 201 | Created | Successful POST (resource created) |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Validation errors, invalid input |
| 401 | Unauthorized | Missing or invalid authentication |
| 403 | Forbidden | Authenticated but not authorized |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate resource (e.g., email exists) |
| 422 | Unprocessable Entity | Business logic validation failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected server error |

---

## Rate Limits

| Endpoint Pattern | Limit | Window |
|-----------------|-------|--------|
| `/auth/login` | 5 requests | 5 minutes |
| `/auth/register/*` | 3 requests | 1 hour |
| `/auth/forgot-password` | 3 requests | 1 hour |
| `/auth/verify-email` | 10 requests | 1 hour |
| All other endpoints | 100 requests | 15 minutes |

---

## Testing with cURL

### Login Example
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "SecurePass123!"
  }'
```

### Get Products with Token
```bash
TOKEN="your-jwt-token-here"

curl -X GET http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN"
```

### Create Order
```bash
curl -X POST http://localhost:8080/api/customer/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "vendorId": "VEN-xyz789",
    "deliveryAddressId": 1,
    "orderLines": [
      {
        "productId": "PROD-VEN-001",
        "quantity": 2
      }
    ]
  }'
```

---

## Postman Collection

Import this environment:
```json
{
  "name": "Afrochow Local",
  "values": [
    {
      "key": "base_url",
      "value": "http://localhost:8080/api",
      "enabled": true
    },
    {
      "key": "access_token",
      "value": "",
      "enabled": true
    }
  ]
}
```

---

## Swagger UI

Access interactive API documentation:
```
http://localhost:8080/api/swagger-ui.html
```

Features:
- Try out endpoints directly
- View request/response schemas
- See authentication requirements
- Download OpenAPI spec
