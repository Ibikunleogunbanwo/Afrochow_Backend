package com.afrochow.notification.service;

import com.afrochow.email.EmailService;
import com.afrochow.notification.dto.NotificationDto;
import com.afrochow.notification.model.Notification;
import com.afrochow.order.model.Order;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.NotificationType;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.common.enums.RelatedEntityType;
import com.afrochow.notification.repository.NotificationRepository;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified Notification Service - Orchestrator for all notification channels
 *
 * This service coordinates notifications across multiple channels:
 * 1. In-App Notifications (Database) - Always created for persistent history
 * 2. Email Notifications - For important/critical events
 * 3. Push Notifications - For real-time mobile updates (future)
 * 4. SMS Notifications - For very critical events (future)
 *
 * All notification methods are @Async to avoid blocking the main request thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    // ========== CREATE NOTIFICATIONS ==========

    /**
     * Create and send a notification to a user
     */
    @Transactional
    public NotificationDto createNotification(
            String userPublicId,
            String title,
            String message,
            NotificationType type,
            RelatedEntityType relatedEntityType,
            String relatedEntityId) {

        User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .createdAt(LocalDateTime.now())
                .isRead(false)
                .build();

        Notification savedNotification = notificationRepository.save(notification);
        return toDto(savedNotification);
    }

    /**
     * Create order update notification
     */
    @Transactional
    public NotificationDto notifyOrderUpdate(String userPublicId, String orderPublicId, String message) {
        return createNotification(
                userPublicId,
                "Order Update",
                message,
                NotificationType.ORDER_UPDATE,
                RelatedEntityType.ORDER,
                orderPublicId
        );
    }

    /**
     * Create delivery update notification
     */
    @Transactional
    public NotificationDto notifyDeliveryUpdate(String userPublicId, String orderPublicId, String message) {
        return createNotification(
                userPublicId,
                "Delivery Update",
                message,
                NotificationType.DELIVERY_UPDATE,
                RelatedEntityType.ORDER,
                orderPublicId
        );
    }

    /**
     * Create payment success notification
     */
    @Transactional
    public NotificationDto notifyPaymentSuccess(String userPublicId, String paymentPublicId, String message) {
        return createNotification(
                userPublicId,
                "Payment Successful",
                message,
                NotificationType.PAYMENT_SUCCESS,
                RelatedEntityType.PAYMENT,
                paymentPublicId
        );
    }

    /**
     * Create promotional notification
     */
    @Transactional
    public NotificationDto sendPromoNotification(String userPublicId, String title, String message) {
        return createNotification(
                userPublicId,
                title,
                message,
                NotificationType.PROMO,
                null,
                null
        );
    }

    /**
     * Create system alert notification
     */
    @Transactional
    public NotificationDto sendSystemAlert(String userPublicId, String title, String message) {
        return createNotification(
                userPublicId,
                title,
                message,
                NotificationType.SYSTEM_ALERT,
                null,
                null
        );
    }

    // ========== ORDER NOTIFICATIONS (Multi-Channel) ==========

    /**
     * Notify customer when order is confirmed (after payment success)
     * Channels: In-App (always) + Email
     */
    @Async
    @Transactional
    public void notifyCustomerOrderConfirmed(Order order) {
        try {
            User customer = order.getCustomer().getUser();
            String vendorName = order.getVendor().getRestaurantName();

            // 1. In-app notification (always)
            createInAppNotification(
                customer,
                NotificationType.ORDER_UPDATE,
                "Order Confirmed",
                "Your order from " + vendorName + " has been confirmed and payment received.",
                RelatedEntityType.ORDER,
                order.getPublicOrderId()
            );

            // 2. Email notification
            emailService.sendOrderConfirmationEmail(
                customer.getEmail(),
                customer.getFirstName(),
                order.getPublicOrderId(),
                vendorName,
                order.getTotalAmount(),
                order.getCreatedAt()
            );

            log.info("Order confirmed notifications sent for order: {}", order.getPublicOrderId());
        } catch (Exception e) {
            log.error("Failed to send order confirmed notifications for order: {}",
                order.getPublicOrderId(), e);
        }
    }

    /**
     * Notify vendor of new order
     * Channels: In-App (always) + Email
     */
    @Async
    @Transactional
    public void notifyVendorNewOrder(Order order) {
        try {
            User vendor = order.getVendor().getUser();
            String customerName = order.getCustomer().getUser().getFirstName() + " " +
                                 order.getCustomer().getUser().getLastName();

            // 1. In-app notification (always)
            createInAppNotification(
                vendor,
                NotificationType.ORDER_UPDATE,
                "New Order Received",
                "New order #" + order.getPublicOrderId() + " from " + customerName,
                RelatedEntityType.ORDER,
                order.getPublicOrderId()
            );

            // 2. Email notification
            emailService.sendNewOrderNotificationToVendor(
                vendor.getEmail(),
                order.getVendor().getRestaurantName(),
                order.getPublicOrderId(),
                customerName,
                order.getTotalAmount()
            );

            log.info("New order notifications sent to vendor for order: {}", order.getPublicOrderId());
        } catch (Exception e) {
            log.error("Failed to send new order notifications to vendor for order: {}",
                order.getPublicOrderId(), e);
        }
    }

    /**
     * Notify customer when order is being prepared
     * Channels: In-App (always) only - not critical enough for email
     */
    @Async
    @Transactional
    public void notifyCustomerOrderPreparing(Order order) {
        try {
            User customer = order.getCustomer().getUser();
            String vendorName = order.getVendor().getRestaurantName();

            // In-app notification only (not critical enough for email)
            createInAppNotification(
                customer,
                NotificationType.ORDER_UPDATE,
                "Order Being Prepared",
                vendorName + " is preparing your order",
                RelatedEntityType.ORDER,
                order.getPublicOrderId()
            );

            log.info("Order preparing notification sent for order: {}", order.getPublicOrderId());
        } catch (Exception e) {
            log.error("Failed to send order preparing notification for order: {}",
                order.getPublicOrderId(), e);
        }
    }

    /**
     * Notify customer when order is ready for pickup/delivery
     * Channels: In-App (always) + Email
     */
    @Async
    @Transactional
    public void notifyCustomerOrderReady(Order order) {
        try {
            User customer = order.getCustomer().getUser();
            String vendorName = order.getVendor().getRestaurantName();
            String message = order.getStatus() == OrderStatus.READY_FOR_PICKUP
                ? "Your order from " + vendorName + " is ready for pickup!"
                : "Your order from " + vendorName + " is ready and will be delivered soon!";

            // 1. In-app notification (always)
            createInAppNotification(
                customer,
                NotificationType.ORDER_UPDATE,
                "Order Ready",
                message,
                RelatedEntityType.ORDER,
                order.getPublicOrderId()
            );

            // 2. Email notification
            emailService.sendOrderStatusUpdateEmail(
                customer.getEmail(),
                customer.getFirstName(),
                order.getPublicOrderId(),
                "PREPARING",
                order.getStatus().toString()
            );

            log.info("Order ready notifications sent for order: {}", order.getPublicOrderId());
        } catch (Exception e) {
            log.error("Failed to send order ready notifications for order: {}",
                order.getPublicOrderId(), e);
        }
    }

    /**
     * Notify customer when order is out for delivery
     * Channels: In-App (always) only
     */
    @Async
    @Transactional
    public void notifyCustomerOrderOutForDelivery(Order order) {
        try {
            User customer = order.getCustomer().getUser();

            // In-app notification (future: add push notification for real-time update)
            createInAppNotification(
                customer,
                NotificationType.DELIVERY_UPDATE,
                "Order Out for Delivery",
                "Your order is on its way!",
                RelatedEntityType.ORDER,
                order.getPublicOrderId()
            );

            log.info("Out for delivery notification sent for order: {}", order.getPublicOrderId());
        } catch (Exception e) {
            log.error("Failed to send out for delivery notification for order: {}",
                order.getPublicOrderId(), e);
        }
    }

    /**
     * Notify customer when order is delivered
     * Channels: In-App (always) + Email
     */
    @Async
    @Transactional
    public void notifyCustomerOrderDelivered(Order order) {
        try {
            User customer = order.getCustomer().getUser();
            String vendorName = order.getVendor().getRestaurantName();

            // 1. In-app notification (always)
            createInAppNotification(
                customer,
                NotificationType.DELIVERY_UPDATE,
                "Order Delivered",
                "Your order from " + vendorName + " has been delivered. Enjoy your meal!",
                RelatedEntityType.ORDER,
                order.getPublicOrderId()
            );

            // 2. Email notification
            emailService.sendOrderStatusUpdateEmail(
                customer.getEmail(),
                customer.getFirstName(),
                order.getPublicOrderId(),
                "OUT_FOR_DELIVERY",
                "DELIVERED"
            );

            log.info("Order delivered notifications sent for order: {}", order.getPublicOrderId());
        } catch (Exception e) {
            log.error("Failed to send order delivered notifications for order: {}",
                order.getPublicOrderId(), e);
        }
    }

    /**
     * Notify customer when order is cancelled
     * Channels: In-App (always) + Email
     */
    @Async
    @Transactional
    public void notifyCustomerOrderCancelled(Order order, String reason) {
        try {
            User customer = order.getCustomer().getUser();
            String vendorName = order.getVendor().getRestaurantName();
            String message = "Your order from " + vendorName + " has been cancelled.";
            if (reason != null && !reason.isEmpty()) {
                message += " Reason: " + reason;
            }

            // 1. In-app notification (always)
            createInAppNotification(
                customer,
                NotificationType.ORDER_UPDATE,
                "Order Cancelled",
                message,
                RelatedEntityType.ORDER,
                order.getPublicOrderId()
            );

            // 2. Email notification
            emailService.sendOrderStatusUpdateEmail(
                customer.getEmail(),
                customer.getFirstName(),
                order.getPublicOrderId(),
                order.getStatus().toString(),
                "CANCELLED"
            );

            log.info("Order cancelled notifications sent for order: {}", order.getPublicOrderId());
        } catch (Exception e) {
            log.error("Failed to send order cancelled notifications for order: {}",
                order.getPublicOrderId(), e);
        }
    }

    // ========== PAYMENT NOTIFICATIONS ==========

    /**
     * Notify customer of successful payment
     * Channels: In-App (always) + Email
     */
    @Async
    @Transactional
    public void notifyPaymentSuccess(String userPublicId, String paymentPublicId,
                                     String orderPublicId, BigDecimal amount) {
        try {
            User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

            // 1. In-app notification (always)
            createInAppNotification(
                user,
                NotificationType.PAYMENT_SUCCESS,
                "Payment Successful",
                "Your payment of $" + String.format("%.2f", amount) + " has been processed successfully",
                RelatedEntityType.PAYMENT,
                paymentPublicId
            );

            // 2. Email notification
            emailService.sendPaymentConfirmationEmail(
                user.getEmail(),
                user.getFirstName(),
                paymentPublicId,
                orderPublicId,
                amount
            );

            log.info("Payment success notifications sent for payment: {}", paymentPublicId);
        } catch (Exception e) {
            log.error("Failed to send payment success notifications for payment: {}",
                paymentPublicId, e);
        }
    }

    /**
     * Notify customer of failed payment
     * Channels: In-App (always) + Email
     */
    @Async
    @Transactional
    public void notifyPaymentFailed(String userPublicId, String orderPublicId, String reason) {
        try {
            User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

            // 1. In-app notification (always)
            createInAppNotification(
                user,
                NotificationType.SYSTEM_ALERT,
                "Payment Failed",
                "Your payment for order #" + orderPublicId + " failed. Please try again.",
                RelatedEntityType.ORDER,
                orderPublicId
            );

            // 2. Email notification
            emailService.sendPaymentFailedEmail(
                user.getEmail(),
                user.getFirstName(),
                orderPublicId,
                reason
            );

            log.info("Payment failed notifications sent for order: {}", orderPublicId);
        } catch (Exception e) {
            log.error("Failed to send payment failed notifications for order: {}",
                orderPublicId, e);
        }
    }

    // ========== REVIEW NOTIFICATIONS ==========

    /**
     * Notify vendor of new review
     * Channels: In-App (always) only
     */
    @Async
    @Transactional
    public void notifyVendorNewReview(String vendorPublicId, String reviewerName,
                                      Integer rating, String reviewType) {
        try {
            User vendor = userRepository.findByPublicUserId(vendorPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

            String stars = "⭐".repeat(rating);
            String message = reviewerName + " left a " + rating + "-star review " + stars +
                           " on your " + reviewType;

            // In-app notification only
            createInAppNotification(
                vendor,
                NotificationType.SYSTEM_ALERT,
                "New Review",
                message,
                RelatedEntityType.REVIEW,
                null
            );

            log.info("New review notification sent to vendor: {}", vendorPublicId);
        } catch (Exception e) {
            log.error("Failed to send new review notification to vendor: {}",
                vendorPublicId, e);
        }
    }

    // ========== FAVORITE NOTIFICATIONS ==========

    /**
     * Notify vendor when they are favorited by a customer
     * Channels: In-App (always) only
     */
    @Async
    @Transactional
    public void notifyVendorFavorited(String vendorPublicId, String customerName) {
        try {
            User vendor = userRepository.findByPublicUserId(vendorPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

            String message = customerName + " added your restaurant to their favorites! ❤️";

            // In-app notification only
            createInAppNotification(
                vendor,
                NotificationType.SYSTEM_ALERT,
                "New Favorite",
                message,
                null,
                null
            );

            log.info("Vendor favorited notification sent to vendor: {}", vendorPublicId);
        } catch (Exception e) {
            log.error("Failed to send vendor favorited notification to vendor: {}",
                vendorPublicId, e);
        }
    }

    // ========== BROADCAST NOTIFICATIONS ==========

    /**
     * Send notification to all users (admin only)
     */
    @Transactional
    public List<NotificationDto> broadcastNotification(
            String title,
            String message,
            NotificationType type) {

        List<User> allUsers = userRepository.findAll();

        List<Notification> notifications = allUsers.stream()
                .map(user -> Notification.builder()
                        .user(user)
                        .title(title)
                        .message(message)
                        .type(type)
                        .createdAt(LocalDateTime.now())
                        .isRead(false)
                        .build())
                .toList();

        List<Notification> savedNotifications = notificationRepository.saveAll(notifications);
        return savedNotifications.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ========== HELPER METHODS ==========

    /**
     * Create in-app notification (stored in database)
     * This is the persistent notification that appears in the web app
     */
    private void createInAppNotification(User user, NotificationType type,
                                        String title, String message,
                                        RelatedEntityType entityType,
                                        String entityId) {
        Notification notification = Notification.builder()
            .user(user)
            .type(type)
            .title(title)
            .message(message)
            .relatedEntityType(entityType)
            .relatedEntityId(entityId)
            .isRead(false)
            .createdAt(LocalDateTime.now())
            .build();

        notificationRepository.save(notification);
    }

    // ========== READ NOTIFICATIONS ==========

    /**
     * Get all notifications for a user
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getUserNotifications(String userPublicId) {
        User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        return notifications.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get unread notifications for a user
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getUnreadNotifications(String userPublicId) {
        User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<Notification> notifications = notificationRepository.findByUserAndIsReadOrderByCreatedAtDesc(user, false);
        return notifications.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get notifications by type
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotificationsByType(String userPublicId, NotificationType type) {
        User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<Notification> notifications = notificationRepository.findByUserAndType(user, type);
        return notifications.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get recent notifications (last 7 days)
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getRecentNotifications(String userPublicId) {
        User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Notification> notifications = notificationRepository
                .findByUserAndCreatedAtAfterOrderByCreatedAtDesc(user, sevenDaysAgo);
        return notifications.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ========== UPDATE NOTIFICATIONS ==========

    /**
     * Mark a notification as read
     */
    @Transactional
    public NotificationDto markAsRead(String userPublicId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        // Validate ownership
        if (!notification.getUser().getPublicUserId().equals(userPublicId)) {
            throw new IllegalStateException("You can only mark your own notifications as read");
        }

        notification.markAsRead();
        Notification updatedNotification = notificationRepository.save(notification);
        return toDto(updatedNotification);
    }

    /**
     * Mark all notifications as read
     */
    @Transactional
    public void markAllAsRead(String userPublicId) {
        User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<Notification> unreadNotifications = notificationRepository.findByUserAndIsReadOrderByCreatedAtDesc(user, false);
        unreadNotifications.forEach(Notification::markAsRead);
        notificationRepository.saveAll(unreadNotifications);
    }

    /**
     * Mark a notification as unread
     */
    @Transactional
    public NotificationDto markAsUnread(String userPublicId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        // Validate ownership
        if (!notification.getUser().getPublicUserId().equals(userPublicId)) {
            throw new IllegalStateException("You can only mark your own notifications as unread");
        }

        notification.markAsUnread();
        Notification updatedNotification = notificationRepository.save(notification);
        return toDto(updatedNotification);
    }

    // ========== DELETE NOTIFICATIONS ==========

    /**
     * Delete a notification
     */
    @Transactional
    public void deleteNotification(String userPublicId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        // Validate ownership
        if (!notification.getUser().getPublicUserId().equals(userPublicId)) {
            throw new IllegalStateException("You can only delete your own notifications");
        }

        notificationRepository.delete(notification);
    }

    /**
     * Delete all read notifications
     */
    @Transactional
    public void deleteAllReadNotifications(String userPublicId) {
        User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<Notification> readNotifications = notificationRepository.findByUserAndIsReadOrderByCreatedAtDesc(user, true);
        notificationRepository.deleteAll(readNotifications);
    }

    // ========== STATISTICS ==========

    /**
     * Get notification statistics for a user
     */
    @Transactional(readOnly = true)
    public NotificationStats getNotificationStats(String userPublicId) {
        User user = userRepository.findByPublicUserId(userPublicId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Long totalCount = notificationRepository.countByUser(user);
        Long unreadCount = notificationRepository.countByUserAndIsRead(user, false);
        Long readCount = totalCount - unreadCount;

        return NotificationStats.builder()
                .totalNotifications(totalCount)
                .unreadNotifications(unreadCount)
                .readNotifications(readCount)
                .build();
    }

    // ========== INNER CLASSES ==========

    @lombok.Data
    @lombok.Builder
    public static class NotificationStats {
        private Long totalNotifications;
        private Long unreadNotifications;
        private Long readNotifications;
    }

    // ========== MAPPING METHODS ==========

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .notificationId(notification.getNotificationId())
                .userName(notification.getUser() != null ? notification.getUser().getFirstName() + " " + notification.getUser().getLastName() : null)
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }
}
