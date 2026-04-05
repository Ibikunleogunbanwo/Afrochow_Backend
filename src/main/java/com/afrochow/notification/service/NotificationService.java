package com.afrochow.notification.service;

import com.afrochow.email.EmailService;
import com.afrochow.notification.dto.BroadcastLogDto;
import com.afrochow.notification.dto.NotificationDto;
import com.afrochow.notification.model.BroadcastLog;
import com.afrochow.notification.model.Notification;
import com.afrochow.notification.repository.BroadcastLogRepository;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.NotificationType;
import com.afrochow.common.enums.RelatedEntityType;
import com.afrochow.notification.repository.NotificationRepository;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import com.afrochow.favorite.event.VendorFavouritedEvent;
import com.afrochow.order.event.CustomerOrderReceivedEvent;
import com.afrochow.order.event.OrderCancelledEvent;
import com.afrochow.order.event.OrderConfirmedEvent;
import com.afrochow.order.event.OrderDeliveredEvent;
import com.afrochow.order.event.OrderOutForDeliveryEvent;
import com.afrochow.order.event.OrderPlacedEvent;
import com.afrochow.order.event.OrderPreparingEvent;
import com.afrochow.order.event.OrderReadyEvent;
import com.afrochow.payment.event.PaymentCapturedEvent;
import com.afrochow.payment.event.PaymentFailedEvent;
import com.afrochow.review.event.VendorReviewedEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified Notification Service — orchestrates all notification channels.
 *
 * Channels:
 *  1. In-App (DB)  — always created for persistent history
 *  2. Email        — for important / critical events
 *  3. Push / SMS   — future
 *
 * All order lifecycle methods are @Async and accept a publicOrderId string
 * (not an Order entity) so they load a fresh entity on their own thread/
 * transaction, avoiding detached-proxy issues.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final BroadcastLogRepository broadcastLogRepository;
    private final UserRepository         userRepository;
    private final OrderRepository        orderRepository;
    private final EmailService           emailService;

    // ========== GENERIC CREATE ==========

    @Transactional
    public NotificationDto createNotification(
            String userPublicId,
            String title,
            String message,
            NotificationType type,
            RelatedEntityType relatedEntityType,
            String relatedEntityId) {

        User user = resolveUser(userPublicId);

        Notification saved = notificationRepository.save(Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .createdAt(LocalDateTime.now())
                .isRead(false)
                .build());

        return toDto(saved);
    }

    @Transactional
    public NotificationDto notifyOrderUpdate(String userPublicId, String orderPublicId, String message) {
        return createNotification(userPublicId, "Order Update", message,
                NotificationType.ORDER_UPDATE, RelatedEntityType.ORDER, orderPublicId);
    }

    @Transactional
    public NotificationDto notifyDeliveryUpdate(String userPublicId, String orderPublicId, String message) {
        return createNotification(userPublicId, "Delivery Update", message,
                NotificationType.DELIVERY_UPDATE, RelatedEntityType.ORDER, orderPublicId);
    }

    @Transactional
    public NotificationDto notifyPaymentSuccess(String userPublicId, String paymentPublicId, String message) {
        return createNotification(userPublicId, "Payment Successful", message,
                NotificationType.PAYMENT_SUCCESS, RelatedEntityType.PAYMENT, paymentPublicId);
    }

    @Transactional
    public NotificationDto sendPromoNotification(String userPublicId, String title, String message) {
        return createNotification(userPublicId, title, message,
                NotificationType.PROMO, null, null);
    }

    @Transactional
    public NotificationDto sendSystemAlert(String userPublicId, String title, String message) {
        return createNotification(userPublicId, title, message,
                NotificationType.SYSTEM_ALERT, null, null);
    }

    // ========== ORDER LIFECYCLE NOTIFICATIONS (Fix 5: accept publicOrderId, load fresh) ==========

    // ── Event-driven entry points ──────────────────────────────────────────────────────────────────
    // These listeners fire AFTER the publishing transaction commits, guaranteeing the order/payment
    // records are visible in the DB before the async notification thread tries to load them.
    // @Async ensures they don't block the HTTP response; @Transactional opens a fresh read context.

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        notifyVendorNewOrder(event.publicOrderId());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        notifyCustomerOrderConfirmed(event.publicOrderId());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        notifyPaymentSuccess(event.userPublicId(), event.paymentId(),
                event.publicOrderId(), event.amount());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerOrderReceived(CustomerOrderReceivedEvent event) {
        notifyCustomerOrderReceived(event.publicOrderId());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        notifyCustomerOrderCancelled(event.publicOrderId(), event.reason(), event.previousStatus());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPreparing(OrderPreparingEvent event) {
        notifyCustomerOrderPreparing(event.publicOrderId());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderReady(OrderReadyEvent event) {
        notifyCustomerOrderReady(event.publicOrderId());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderOutForDelivery(OrderOutForDeliveryEvent event) {
        notifyCustomerOrderOutForDelivery(event.publicOrderId());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        notifyCustomerOrderDelivered(event.publicOrderId());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        notifyPaymentFailed(event.userPublicId(), event.publicOrderId(), event.reason());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVendorReviewed(VendorReviewedEvent event) {
        notifyVendorNewReview(event.vendorPublicId(), event.reviewerName(), event.rating(), event.reviewType());
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVendorFavourited(VendorFavouritedEvent event) {
        notifyVendorFavorited(event.vendorPublicId(), event.customerName());
    }

    // ──────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Notify customer when order is confirmed (after payment).
     * Channels: In-App + Email
     */
    @Async
    @Transactional
    public void notifyCustomerOrderConfirmed(String publicOrderId) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            User customer = order.getCustomer().getUser();
            if (!areNotificationsEnabled(customer)) return;
            String vendorName = order.getVendor().getRestaurantName();

            createInAppNotification(customer, NotificationType.ORDER_UPDATE,
                    "Order Confirmed",
                    "Your order from " + vendorName + " has been confirmed and payment received.",
                    RelatedEntityType.ORDER, publicOrderId);

            emailService.sendOrderConfirmationEmail(
                    customer.getEmail(), customer.getFirstName(),
                    publicOrderId, vendorName,
                    order.getTotalAmount(), order.getCreatedAt());

            log.info("Order confirmed notifications sent for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to send order confirmed notifications for order: {}", publicOrderId, e);
        }
    }

    /**
     * Notify vendor of a new order.
     * Channels: In-App + Email
     * Fix 1: uses NEW_ORDER type instead of ORDER_UPDATE.
     */
    @Async
    @Transactional
    public void notifyVendorNewOrder(String publicOrderId) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            User vendor = order.getVendor().getUser();
            String customerName = order.getCustomer().getUser().getFirstName() + " "
                    + order.getCustomer().getUser().getLastName();

            createInAppNotification(vendor, NotificationType.NEW_ORDER,   // Fix 1
                    "New Order Received",
                    "New order #" + publicOrderId + " from " + customerName,
                    RelatedEntityType.ORDER, publicOrderId);

            emailService.sendNewOrderNotificationToVendor(
                    vendor.getEmail(), order.getVendor().getRestaurantName(),
                    publicOrderId, customerName, order.getTotalAmount());

            log.info("New order notifications sent to vendor for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to send new order notifications to vendor for order: {}", publicOrderId, e);
        }
    }

    /**
     * Notify customer immediately after their order is placed and payment authorised.
     * Fires before the vendor has acted — reassures the customer the order was received.
     * Channels: In-App only (email confirmation comes later when vendor accepts)
     */
    @Async
    @Transactional
    public void notifyCustomerOrderReceived(String publicOrderId) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            User customer = order.getCustomer().getUser();
            if (!areNotificationsEnabled(customer)) return;

            String vendorName = order.getVendor().getRestaurantName();

            createInAppNotification(customer, NotificationType.ORDER_UPDATE,
                    "Order Received",
                    "Your order from " + vendorName + " has been received and is waiting for confirmation.",
                    RelatedEntityType.ORDER, publicOrderId);

            emailService.sendOrderReceivedEmail(
                    customer.getEmail(), customer.getFirstName(),
                    publicOrderId, vendorName,
                    order.getTotalAmount(), order.getCreatedAt());

            log.info("Customer order received notifications sent for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to send customer order received notifications for order: {}", publicOrderId, e);
        }
    }

    /**
     * Notify customer when order is being prepared.
     * Channels: In-App only
     */
    @Async
    @Transactional
    public void notifyCustomerOrderPreparing(String publicOrderId) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            if (!areNotificationsEnabled(order.getCustomer().getUser())) return;

            createInAppNotification(order.getCustomer().getUser(), NotificationType.ORDER_UPDATE,
                    "Order Being Prepared",
                    order.getVendor().getRestaurantName() + " is preparing your order",
                    RelatedEntityType.ORDER, publicOrderId);

            log.info("Order preparing notification sent for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to send order preparing notification for order: {}", publicOrderId, e);
        }
    }

    /**
     * Notify customer when order is ready for pickup / delivery.
     * Channels: In-App + Email
     */
    @Async
    @Transactional
    public void notifyCustomerOrderReady(String publicOrderId) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            User customer = order.getCustomer().getUser();
            if (!areNotificationsEnabled(customer)) return;

            String vendorName = order.getVendor().getRestaurantName();
            String message = "PICKUP".equalsIgnoreCase(order.getFulfillmentType())
                    ? "Your order from " + vendorName + " is ready for pickup!"
                    : "Your order from " + vendorName + " is ready and will be delivered soon!";

            createInAppNotification(customer, NotificationType.ORDER_UPDATE,
                    "Order Ready", message, RelatedEntityType.ORDER, publicOrderId);

            emailService.sendOrderStatusUpdateEmail(
                    customer.getEmail(), customer.getFirstName(),
                    publicOrderId, "PREPARING", order.getStatus().toString());

            log.info("Order ready notifications sent for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to send order ready notifications for order: {}", publicOrderId, e);
        }
    }

    /**
     * Notify customer when order is out for delivery.
     * Channels: In-App only
     */
    @Async
    @Transactional
    public void notifyCustomerOrderOutForDelivery(String publicOrderId) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            if (!areNotificationsEnabled(order.getCustomer().getUser())) return;

            createInAppNotification(order.getCustomer().getUser(), NotificationType.DELIVERY_UPDATE,
                    "Order Out for Delivery",
                    "Your order is on its way!",
                    RelatedEntityType.ORDER, publicOrderId);

            log.info("Out for delivery notification sent for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to send out for delivery notification for order: {}", publicOrderId, e);
        }
    }

    /**
     * Notify customer when order is delivered.
     * Channels: In-App + Email
     */
    @Async
    @Transactional
    public void notifyCustomerOrderDelivered(String publicOrderId) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            User customer = order.getCustomer().getUser();
            if (!areNotificationsEnabled(customer)) return;

            String vendorName = order.getVendor().getRestaurantName();
            String previousStatus = "PICKUP".equalsIgnoreCase(order.getFulfillmentType())
                    ? "READY_FOR_PICKUP" : "OUT_FOR_DELIVERY";

            createInAppNotification(customer, NotificationType.DELIVERY_UPDATE,
                    "Order Delivered",
                    "Your order from " + vendorName + " has been delivered. Enjoy your meal!",
                    RelatedEntityType.ORDER, publicOrderId);

            emailService.sendOrderStatusUpdateEmail(
                    customer.getEmail(), customer.getFirstName(),
                    publicOrderId, previousStatus, "DELIVERED");

            log.info("Order delivered notifications sent for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to send order delivered notifications for order: {}", publicOrderId, e);
        }
    }

    /**
     * Notify customer when order is cancelled.
     * Channels: In-App + Email
     */
    @Async
    @Transactional
    public void notifyCustomerOrderCancelled(String publicOrderId, String reason, String previousStatus) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            User customer = order.getCustomer().getUser();
            if (!areNotificationsEnabled(customer)) return;

            String vendorName = order.getVendor().getRestaurantName();
            String message = "Your order from " + vendorName + " has been cancelled.";
            if (reason != null && !reason.isEmpty()) message += " Reason: " + reason;

            createInAppNotification(customer, NotificationType.ORDER_UPDATE,
                    "Order Cancelled", message, RelatedEntityType.ORDER, publicOrderId);

            emailService.sendOrderStatusUpdateEmail(
                    customer.getEmail(), customer.getFirstName(),
                    publicOrderId, previousStatus, "CANCELLED");

            log.info("Order cancelled notifications sent for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to send order cancelled notifications for order: {}", publicOrderId, e);
        }
    }

    // ========== PAYMENT NOTIFICATIONS ==========

    @Async
    @Transactional
    public void notifyPaymentSuccess(String userPublicId, String paymentPublicId,
                                     String orderPublicId, BigDecimal amount) {
        try {
            User user = resolveUser(userPublicId);
            if (!areNotificationsEnabled(user)) return;

            createInAppNotification(user, NotificationType.PAYMENT_SUCCESS,
                    "Payment Successful",
                    "Your payment of $" + String.format("%.2f", amount) + " has been processed successfully",
                    RelatedEntityType.PAYMENT, paymentPublicId);

            emailService.sendPaymentConfirmationEmail(
                    user.getEmail(), user.getFirstName(),
                    paymentPublicId, orderPublicId, amount);

            log.info("Payment success notifications sent for payment: {}", paymentPublicId);
        } catch (Exception e) {
            log.error("Failed to send payment success notifications for payment: {}", paymentPublicId, e);
        }
    }

    @Async
    @Transactional
    public void notifyPaymentFailed(String userPublicId, String orderPublicId, String reason) {
        try {
            User user = resolveUser(userPublicId);
            if (!areNotificationsEnabled(user)) return;

            createInAppNotification(user, NotificationType.SYSTEM_ALERT,
                    "Payment Failed",
                    "Your payment for order #" + orderPublicId + " failed. Please try again.",
                    RelatedEntityType.ORDER, orderPublicId);

            emailService.sendPaymentFailedEmail(
                    user.getEmail(), user.getFirstName(), orderPublicId, reason);

            log.info("Payment failed notifications sent for order: {}", orderPublicId);
        } catch (Exception e) {
            log.error("Failed to send payment failed notifications for order: {}", orderPublicId, e);
        }
    }

    // ========== REVIEW & FAVORITE NOTIFICATIONS ==========

    @Async
    @Transactional
    public void notifyVendorNewReview(String vendorPublicId, String reviewerName,
                                      Integer rating, String reviewType) {
        try {
            User vendor = resolveUser(vendorPublicId);

            String stars = "⭐".repeat(rating);
            createInAppNotification(vendor, NotificationType.SYSTEM_ALERT,
                    "New Review",
                    reviewerName + " left a " + rating + "-star review " + stars + " on your " + reviewType,
                    RelatedEntityType.REVIEW, null);

            log.info("New review notification sent to vendor: {}", vendorPublicId);
        } catch (Exception e) {
            log.error("Failed to send new review notification to vendor: {}", vendorPublicId, e);
        }
    }

    @Async
    @Transactional
    public void notifyVendorFavorited(String vendorPublicId, String customerName) {
        try {
            User vendor = resolveUser(vendorPublicId);

            createInAppNotification(vendor, NotificationType.SYSTEM_ALERT,
                    "New Favorite",
                    customerName + " added your restaurant to their favorites! ❤️",
                    null, null);

            log.info("Vendor favorited notification sent to vendor: {}", vendorPublicId);
        } catch (Exception e) {
            log.error("Failed to send vendor favorited notification to vendor: {}", vendorPublicId, e);
        }
    }

    // ========== BROADCAST (Fix 4: single SQL INSERT SELECT) ==========

    @Transactional
    public void broadcastNotification(com.afrochow.notification.dto.BroadcastNotificationRequestDto dto, String sentBy) {
        final int batchSize = 500;

        long recipientCount = switch (dto.getTargetAudience()) {
            case CUSTOMERS -> userRepository.countByRole(com.afrochow.common.enums.Role.CUSTOMER);
            case VENDORS   -> userRepository.countByRole(com.afrochow.common.enums.Role.VENDOR);
            case ALL       -> userRepository.count();
        };

        Pageable pageable = PageRequest.of(0, batchSize);
        while (true) {
            Page<User> page = switch (dto.getTargetAudience()) {
                case CUSTOMERS -> userRepository.findAllByRole(com.afrochow.common.enums.Role.CUSTOMER, pageable);
                case VENDORS   -> userRepository.findAllByRole(com.afrochow.common.enums.Role.VENDOR, pageable);
                case ALL       -> userRepository.findAll(pageable);
            };

            if (page.isEmpty()) {
                break;
            }

            List<Notification> notifications = page.getContent().stream()
                    .map(user -> Notification.builder()
                            .user(user)
                            .title(dto.getTitle())
                            .message(dto.getMessage())
                            .type(dto.getType())
                            .relatedEntityType(null)
                            .relatedEntityId(null)
                            .createdAt(LocalDateTime.now())
                            .isRead(false)
                            .build())
                    .toList();

            notificationRepository.saveAll(notifications);

            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        broadcastLogRepository.save(BroadcastLog.builder()
                .title(dto.getTitle())
                .message(dto.getMessage())
                .type(dto.getType())
                .targetAudience(dto.getTargetAudience().name())
                .recipientCount((int) recipientCount)
                .sentAt(java.time.LocalDateTime.now())
                .sentBy(sentBy)
                .build());

        log.info("Broadcast notification sent to {} recipient(s) [audience={}]: [{}] {}",
                recipientCount, dto.getTargetAudience(), dto.getType(), dto.getTitle());
    }

    @Transactional(readOnly = true)
    public Page<BroadcastLogDto> getBroadcastHistory(Pageable pageable) {
        return broadcastLogRepository.findAllByOrderBySentAtDesc(pageable)
                .map(log -> BroadcastLogDto.builder()
                        .id(log.getId())
                        .title(log.getTitle())
                        .message(log.getMessage())
                        .type(log.getType())
                        .targetAudience(log.getTargetAudience())
                        .recipientCount(log.getRecipientCount())
                        .sentAt(log.getSentAt())
                        .sentBy(log.getSentBy())
                        .build());
    }

    // ========== READ ==========

    /** Fix 3: paginated — use page/size query params from the controller. */
    @Transactional(readOnly = true)
    public Page<NotificationDto> getUserNotifications(String userPublicId, Pageable pageable) {
        User user = resolveUser(userPublicId);
        return notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getUnreadNotifications(String userPublicId) {
        User user = resolveUser(userPublicId);
        return notificationRepository.findByUserAndIsReadOrderByCreatedAtDesc(user, false)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotificationsByType(String userPublicId, NotificationType type) {
        User user = resolveUser(userPublicId);
        return notificationRepository.findByUserAndType(user, type)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getRecentNotifications(String userPublicId) {
        User user = resolveUser(userPublicId);
        return notificationRepository
                .findByUserAndCreatedAtAfterOrderByCreatedAtDesc(user, LocalDateTime.now().minusDays(7))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ========== UPDATE ==========

    @Transactional
    public NotificationDto markAsRead(String userPublicId, Long notificationId) {
        Notification n = getOwnedNotification(userPublicId, notificationId);
        n.markAsRead();
        return toDto(notificationRepository.save(n));
    }

    @Transactional
    public NotificationDto markAsUnread(String userPublicId, Long notificationId) {
        Notification n = getOwnedNotification(userPublicId, notificationId);
        n.markAsUnread();
        return toDto(notificationRepository.save(n));
    }

    @Transactional
    public void markAllAsRead(String userPublicId) {
        User user = resolveUser(userPublicId);
        List<Notification> unread = notificationRepository
                .findByUserAndIsReadOrderByCreatedAtDesc(user, false);
        unread.forEach(Notification::markAsRead);
        notificationRepository.saveAll(unread);
    }

    // ========== DELETE ==========

    @Transactional
    public void deleteNotification(String userPublicId, Long notificationId) {
        Notification n = getOwnedNotification(userPublicId, notificationId);
        notificationRepository.delete(n);
    }

    /** Fix 2: single DELETE statement instead of fetch-then-delete. */
    @Transactional
    public void deleteAllReadNotifications(String userPublicId) {
        User user = resolveUser(userPublicId);
        notificationRepository.deleteAllReadByUser(user);
    }

    // ========== STATS ==========

    @Transactional(readOnly = true)
    public NotificationStats getNotificationStats(String userPublicId) {
        User user = resolveUser(userPublicId);
        Long total  = notificationRepository.countByUser(user);
        Long unread = notificationRepository.countByUserAndIsRead(user, false);
        return NotificationStats.builder()
                .totalNotifications(total)
                .unreadNotifications(unread)
                .readNotifications(total - unread)
                .build();
    }

    // ========== HELPERS ==========

    /**
     * Resolve a user by publicUserId, email, or username — whichever matches.
     * authentication.getName() returns whichever value CustomUserDetails.getUsername()
     * is set to (currently the plain username string), so we try all three fields.
     */
    private User resolveUser(String identifier) {
        return userRepository.findByPublicUserId(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + identifier));
    }

    /** Fix 5: load Order fresh on the async thread to avoid detached-proxy issues. */
    private Order loadOrder(String publicOrderId) {
        return orderRepository.findByPublicOrderId(publicOrderId).orElseGet(() -> {
            log.warn("Order not found for notification: {}", publicOrderId);
            return null;
        });
    }

    private Notification getOwnedNotification(String userIdentifier, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        User owner = n.getUser();
        boolean isOwner = owner.getPublicUserId().equals(userIdentifier)
                || owner.getEmail().equals(userIdentifier)
                || owner.getUsername().equals(userIdentifier);
        if (!isOwner) {
            throw new IllegalStateException("You can only modify your own notifications");
        }
        return n;
    }

    /** Returns false if the user is a customer who has opted out of notifications. */
    private boolean areNotificationsEnabled(User user) {
        if (!user.isCustomer()) return true;
        com.afrochow.customer.model.CustomerProfile profile = user.getCustomerProfile();
        return profile == null || Boolean.TRUE.equals(profile.getNotificationsEnabled());
    }

    private void createInAppNotification(User user, NotificationType type,
                                         String title, String message,
                                         RelatedEntityType entityType, String entityId) {
        notificationRepository.save(Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityType(entityType)
                .relatedEntityId(entityId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .notificationId(n.getNotificationId())
                .userName(n.getUser() != null
                        ? n.getUser().getFirstName() + " " + n.getUser().getLastName() : null)
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .relatedEntityType(n.getRelatedEntityType())
                .relatedEntityId(n.getRelatedEntityId())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
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
}
