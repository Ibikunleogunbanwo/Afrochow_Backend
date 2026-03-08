# Unified Notification Architecture

## Overview

The Afrochow backend now uses a **unified multi-channel notification system** that coordinates notifications across four channels:

1. **In-App Notifications** (Database) - Always created for persistent history
2. **Email Notifications** - For important/critical events
3. **Push Notifications** (Future) - For real-time mobile updates
4. **SMS Notifications** (Future) - For very critical events

All notification methods are `@Async` to avoid blocking the main request thread.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│           NotificationService (Orchestrator)         │
│                                                      │
│  • Coordinates all notification channels             │
│  • Business logic for which channels to use          │
│  • Async execution (@Async)                          │
└──────────────────┬──────────────────────────────────┘
                   │
        ┌──────────┼──────────┬──────────────┐
        │          │           │              │
        ▼          ▼           ▼              ▼
┌──────────┐ ┌──────────┐ ┌───────┐ ┌────────────┐
│ In-App   │ │  Email   │ │ Push  │ │    SMS     │
│(Database)│ │ Service  │ │Service│ │  Service   │
└──────────┘ └──────────┘ └───────┘ └────────────┘
  Always       Critical     Future      Future
   Used         Events       (Stub)      (Stub)
```

## Services

### NotificationService (Orchestrator)
**Location**: `src/main/java/com/afrochow/notification/service/NotificationService.java`

Main orchestrator that coordinates all notification channels. Determines which channels to use based on the event type and importance.

**Key Methods**:
- `notifyCustomerOrderConfirmed(Order)` - In-App + Email
- `notifyVendorNewOrder(Order)` - In-App + Email
- `notifyCustomerOrderPreparing(Order)` - In-App only
- `notifyCustomerOrderReady(Order)` - In-App + Email
- `notifyCustomerOrderOutForDelivery(Order)` - In-App only
- `notifyCustomerOrderDelivered(Order)` - In-App + Email
- `notifyCustomerOrderCancelled(Order, reason)` - In-App + Email
- `notifyPaymentSuccess(...)` - In-App + Email
- `notifyPaymentFailed(...)` - In-App + Email
- `notifyVendorNewReview(...)` - In-App only

### EmailService
**Location**: `src/main/java/com/afrochow/email/EmailService.java`

Handles all email notifications using JavaMailSender and Thymeleaf templates.

**Key Features**:
- HTML emails with Thymeleaf templates
- Support for order confirmations, status updates, payment confirmations
- Vendor notifications
- Authentication emails (password reset, email verification)

### PushNotificationService (Stub)
**Location**: `src/main/java/com/afrochow/notification/service/PushNotificationService.java`

Stub service for future push notification implementation.

**Recommended Integration**:
- Firebase Cloud Messaging (FCM) for Android/iOS
- Web Push API for browsers

### SmsService (Stub)
**Location**: `src/main/java/com/afrochow/notification/service/SmsService.java`

Stub service for future SMS notification implementation.

**Recommended Integration**:
- Twilio SMS API
- AWS SNS
- Africa's Talking (for African markets)

**Reserved for critical events**:
- Payment confirmations
- Order delivered
- Security alerts

## Notification Flow by Event

### 1. Order Confirmed (After Payment Success)
**Channels**: In-App + Email
- Customer receives order confirmation with details
- Vendor receives new order notification
- Both users get in-app notification (database) + email

**Code**:
```java
notificationService.notifyCustomerOrderConfirmed(order);
notificationService.notifyVendorNewOrder(order);
```

### 2. Order Preparing
**Channels**: In-App only
- Customer sees notification in web app
- Not critical enough for email

**Code**:
```java
notificationService.notifyCustomerOrderPreparing(order);
```

### 3. Order Ready for Pickup/Delivery
**Channels**: In-App + Email
- Customer receives important notification
- Email sent to confirm order is ready

**Code**:
```java
notificationService.notifyCustomerOrderReady(order);
```

### 4. Order Out for Delivery
**Channels**: In-App only
- Real-time update in web app
- Future: Push notification for mobile users

**Code**:
```java
notificationService.notifyCustomerOrderOutForDelivery(order);
```

### 5. Order Delivered
**Channels**: In-App + Email
- Critical milestone notification
- Confirms successful delivery

**Code**:
```java
notificationService.notifyCustomerOrderDelivered(order);
```

### 6. Order Cancelled
**Channels**: In-App + Email
- Important status change
- Includes cancellation reason

**Code**:
```java
notificationService.notifyCustomerOrderCancelled(order, "Reason here");
```

### 7. Payment Success
**Channels**: In-App + Email
- Critical financial transaction confirmation

**Code**:
```java
notificationService.notifyPaymentSuccess(userPublicId, paymentId, orderId, amount);
```

### 8. Payment Failed
**Channels**: In-App + Email
- Critical alert requiring user action

**Code**:
```java
notificationService.notifyPaymentFailed(userPublicId, orderId, reason);
```

### 9. New Review
**Channels**: In-App only
- Vendor notified of new review
- Includes rating and reviewer name

**Code**:
```java
notificationService.notifyVendorNewReview(vendorPublicId, reviewerName, rating, reviewType);
```

### 10. Email Verified / Welcome
**Channels**: In-App only
- User receives welcome notification after email verification
- Different messages for customer, vendor, and admin roles

**Code**:
```java
notificationService.createNotification(
    user.getPublicUserId(),
    "Welcome to Afrochow! 🎉",
    welcomeMessage,
    NotificationType.SYSTEM_ALERT,
    null,
    null
);
```

### 11. Password Reset Requested
**Channels**: In-App only
- Security alert when password reset is requested
- Warns user if they didn't request it

**Code**:
```java
notificationService.createNotification(
    user.getPublicUserId(),
    "Password Reset Requested",
    "A password reset was requested for your account...",
    NotificationType.SYSTEM_ALERT,
    null,
    null
);
```

### 12. Password Changed Successfully
**Channels**: In-App only
- Security confirmation after password change
- Alerts user to contact support if unauthorized

**Code**:
```java
notificationService.createNotification(
    user.getPublicUserId(),
    "Password Changed Successfully",
    "Your password has been changed successfully...",
    NotificationType.SYSTEM_ALERT,
    null,
    null
);
```

### 13. Vendor Favorited
**Channels**: In-App only
- Vendor notified when customer adds them to favorites
- Helps vendors understand customer engagement

**Code**:
```java
notificationService.notifyVendorFavorited(vendorPublicId, customerName);
```

## Services Updated

### OrderService
**Location**: `src/main/java/com/afrochow/order/service/OrderService.java`

**Changes**:
- Replaced `EmailService` dependency with `NotificationService`
- Updated all order status change methods to use unified notifications
- Methods updated:
  - `acceptOrder()` - Order confirmed
  - `rejectOrder()` - Order cancelled by vendor
  - `startPreparingOrder()` - Order preparing
  - `markOrderReady()` - Order ready
  - `markOrderOutForDelivery()` - Out for delivery
  - `markOrderDelivered()` - Delivered
  - `cancelCustomerOrder()` - Cancelled by customer

### ReviewService
**Location**: `src/main/java/com/afrochow/review/service/ReviewService.java`

**Changes**:
- Added `NotificationService` dependency
- Updated `createReview()` to notify vendor of new review

### PaymentService
**Location**: `src/main/java/com/afrochow/payment/service/PaymentService.java`

**Changes**:
- Replaced `EmailService` dependency with `NotificationService`
- Updated `processPayment()` to use unified notifications for success/failure
- Updated `refundPayment()` to send in-app notification

### AuthenticationService
**Location**: `src/main/java/com/afrochow/auth/service/AuthenticationService.java`

**Changes**:
- Added `NotificationService` dependency (keeps `EmailService` for auth emails)
- Added in-app notification when email is verified (welcome message)
- Added in-app notification when password reset is requested (security alert)
- Added in-app notification when password is successfully changed (security confirmation)

**Note**: Authentication emails (verification, password reset) still use `EmailService` directly as they are critical authentication flows that don't need to be routed through the orchestrator.

### FavoriteService
**Location**: `src/main/java/com/afrochow/favorite/service/FavoriteService.java`

**Changes**:
- Added `NotificationService` dependency
- Updated `addFavorite()` to notify vendor when customer favorites their restaurant
- In-app notification only (not critical enough for email)

## Configuration

### Enable Async Processing
**File**: `src/main/java/com/afrochow/AfrochowApplication.java`

Added `@EnableAsync` annotation to enable asynchronous notification processing:
```java
@EnableAsync
@SpringBootApplication
public class AfrochowApplication {
    // ...
}
```

### Email Configuration
Email settings are configured in `application.properties`:
```properties
spring.mail.enabled=true
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.from=${MAIL_FROM}
spring.mail.from-name=Afrochow
app.name=Afrochow
app.url=https://afrochow.com
```

## Web App Integration

### In-App Notifications
Users can access their in-app notifications through the existing API endpoints:

**Get all notifications**:
```
GET /notifications
```

**Get unread notifications**:
```
GET /notifications/unread
```

**Mark as read**:
```
PATCH /notifications/{id}/read
```

**Delete notification**:
```
DELETE /notifications/{id}
```

These endpoints are served by `NotificationController.java` and return notifications stored in the database.

## Future Enhancements

### 1. Push Notifications
When mobile app is developed:
- Implement Firebase Cloud Messaging
- Store device tokens in database
- Send push for real-time events (order status changes, delivery updates)

### 2. SMS Notifications
When SMS provider is funded:
- Integrate Twilio or Africa's Talking
- Send SMS for critical events only (payment confirmed, order delivered)
- Implement rate limiting to control costs

### 3. User Preferences
Allow users to configure notification preferences:
- Enable/disable email notifications per event type
- Enable/disable push notifications
- Notification frequency settings
- Quiet hours configuration

### 4. Notification Templates
Create database-driven notification templates:
- Allow admins to customize notification content
- Support multiple languages
- A/B testing for notification messaging

## Benefits

1. **Separation of Concerns**: Notification logic is centralized in NotificationService
2. **Easy to Extend**: Adding new channels (Push, SMS) doesn't require changing business logic
3. **Consistent Experience**: All notifications follow same patterns
4. **Async Processing**: Notifications don't block main request threads
5. **User Preferences**: Easy to add per-user notification settings
6. **Failure Isolation**: If email fails, in-app notification still works
7. **Audit Trail**: In-app notifications provide persistent history

## Testing

When testing notifications:
1. **Development**: Set `spring.mail.enabled=false` to skip emails and just log
2. **Staging**: Use test email addresses to verify email delivery
3. **Production**: Monitor email delivery rates and failure logs

## Monitoring

Important metrics to track:
- In-app notification creation rate
- Email delivery success/failure rates
- Notification processing time (async)
- User engagement with notifications (read rates)
- Email open rates (when email tracking is added)
