package com.afrochow.notification.service;

import com.afrochow.email.EmailService;
import com.afrochow.notification.dto.BroadcastLogDto;
import com.afrochow.notification.dto.BroadcastNotificationRequestDto;
import com.afrochow.notification.dto.NotificationDto;
import com.afrochow.notification.model.BroadcastLog;
import com.afrochow.notification.model.Notification;
import com.afrochow.notification.repository.BroadcastLogRepository;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.NotificationType;
import com.afrochow.common.enums.RelatedEntityType;
import com.afrochow.common.enums.Role;
import com.afrochow.notification.repository.NotificationRepository;
import com.afrochow.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * Order lifecycle methods accept a publicOrderId string (not an Order entity)
 * so they load a fresh entity in their own transaction, avoiding detached-proxy
 * issues. These methods are synchronous for outbox dispatch so failures can be
 * retried by the poller.
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
    private final OutboxEventService     outboxEventService;

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

    // ========== ORDER LIFECYCLE NOTIFICATIONS ==========

    // Entry points are now called directly by OutboxPoller after it reads
    // the committed outbox row — no Spring events needed.

    /**
     * Notify customer when order is confirmed (after payment).
     * Channels: In-App + Email
     */
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
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    /**
     * Notify vendor of a new order.
     * Channels: In-App + Email
     * Fix 1: uses NEW_ORDER type instead of ORDER_UPDATE.
     */
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
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    /**
     * Notify customer immediately after their order is placed and payment authorised.
     * Fires before the vendor has acted — reassures the customer the order was received.
     * Channels: In-App only (email confirmation comes later when vendor accepts)
     */
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
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    /**
     * Notify customer when order is being prepared.
     * Channels: In-App + Email
     */
    @Transactional
    public void notifyCustomerOrderPreparing(String publicOrderId) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            User customer = order.getCustomer().getUser();
            if (!areNotificationsEnabled(customer)) return;

            createInAppNotification(customer, NotificationType.ORDER_UPDATE,
                    "Order Being Prepared",
                    order.getVendor().getRestaurantName() + " is preparing your order",
                    RelatedEntityType.ORDER, publicOrderId);

            emailService.sendOrderStatusUpdateEmail(
                    customer.getEmail(), customer.getFirstName(),
                    publicOrderId, "CONFIRMED", "PREPARING");

            log.info("Order preparing notification sent for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to send order preparing notification for order: {}", publicOrderId, e);
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    /**
     * Notify customer when order is ready for pickup / delivery.
     * Channels: In-App + Email
     */
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
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    /**
     * Notify customer when order is out for delivery.
     * Channels: In-App only
     */
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
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    /**
     * Notify customer when order is delivered.
     * Channels: In-App + Email
     */
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
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    /**
     * Notify customer when order is cancelled.
     * Channels: In-App + Email
     */
    @Transactional
    public void notifyCustomerOrderCancelled(String publicOrderId, String reason,
                                              String previousStatus, String cancelledBy) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            User customer = order.getCustomer().getUser();
            if (!areNotificationsEnabled(customer)) return;

            String vendorName = order.getVendor().getRestaurantName();

            // Title and message vary by who initiated the cancellation
            String title;
            String message;
            switch (cancelledBy != null ? cancelledBy : "UNKNOWN") {
                case "SYSTEM" -> {
                    title   = "Order Automatically Cancelled";
                    message = "Your order from " + vendorName + " was automatically cancelled because "
                            + "the restaurant did not respond in time. You have not been charged — "
                            + "any authorization hold on your card will be released within 5–7 business days.";
                }
                case "SYSTEM_OVERDUE" -> {
                    // Distinct from plain "SYSTEM": this fires from OrderFulfillmentOverdueScheduler,
                    // after the vendor had already accepted the order (payment was CAPTURED, not just
                    // authorised), so this must say "refunded", not "hold released".
                    title   = "Order Automatically Cancelled and Refunded";
                    message = "Your order from " + vendorName + " was automatically cancelled because "
                            + "the restaurant did not complete it in time. You have been refunded — "
                            + "funds will appear back on your card within 5–10 business days.";
                }
                case "VENDOR" -> {
                    title   = "Order Declined by Restaurant";
                    message = "Unfortunately, " + vendorName + " has declined your order."
                            + (reason != null && !reason.isEmpty() ? " Reason: " + reason : "")
                            + " You have not been charged — any authorization hold on your card will be released within 5–7 business days.";
                }
                case "ADMIN" -> {
                    title   = "Order Cancelled";
                    message = "Your order from " + vendorName + " has been cancelled by Afrochow support."
                            + (reason != null && !reason.isEmpty() ? " Reason: " + reason : "")
                            + " You have not been charged — any authorization hold on your card will be released within 5–7 business days."
                            + " If you have questions, please contact support.";
                }
                default -> {
                    // CUSTOMER or legacy events without cancelledBy
                    title   = "Order Cancelled";
                    message = "Your order from " + vendorName + " has been cancelled."
                            + (reason != null && !reason.isEmpty() ? " Reason: " + reason : "")
                            + " You have not been charged — any authorization hold on your card will be released within 5–7 business days.";
                }
            }

            createInAppNotification(customer, NotificationType.ORDER_UPDATE,
                    title, message, RelatedEntityType.ORDER, publicOrderId);

            emailService.sendOrderStatusUpdateEmail(
                    customer.getEmail(), customer.getFirstName(),
                    publicOrderId, previousStatus, "CANCELLED");

            log.info("Order cancelled notifications sent for order: {} cancelledBy={}", publicOrderId, cancelledBy);
        } catch (Exception e) {
            log.error("Failed to send order cancelled notifications for order: {}", publicOrderId, e);
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    // ========== PAYMENT NOTIFICATIONS ==========

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
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    @Transactional
    public void notifyPaymentFailed(String userPublicId, String orderPublicId, String reason) {
        try {
            User user = resolveUser(userPublicId);
            if (!areNotificationsEnabled(user)) return;

            // A payment failure on the very first charge attempt (OrderService.createOrder
            // → PaymentService.chargeOrder) rolls back the ENTIRE order — nothing persists.
            // But this method is also reached from retryPayment/confirmAfter3ds failures,
            // where the order genuinely does exist and is just sitting with a FAILED
            // payment. Those are different customer experiences: one has a real order to
            // retry against, the other needs to start over from the cart. Without this
            // check, every customer got told "your order is saved, retry anytime" with a
            // link to an order-confirmation page that 404s for the never-persisted case.
            boolean orderExists = orderRepository.findByPublicOrderId(orderPublicId).isPresent();

            createInAppNotification(user, NotificationType.SYSTEM_ALERT,
                    "Payment Failed",
                    orderExists
                            ? "Your payment for order #" + orderPublicId + " failed. Please try again."
                            : "Your order could not be placed because the payment failed. Please try again from your cart.",
                    orderExists ? RelatedEntityType.ORDER : null,
                    orderExists ? orderPublicId : null);

            emailService.sendPaymentFailedEmail(
                    user.getEmail(), user.getFirstName(), orderPublicId, reason, orderExists);

            log.info("Payment failed notifications sent for order: {} orderExists={}", orderPublicId, orderExists);
        } catch (Exception e) {
            log.error("Failed to send payment failed notifications for order: {}", orderPublicId, e);
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    // ========== REVIEW & FAVORITE NOTIFICATIONS ==========

    @Transactional
    public void notifyVendorNewReview(String vendorPublicId, String reviewerName,
                                      Integer rating, String reviewType) {
        try {
            User vendor = resolveUser(vendorPublicId);

            String stars = "⭐".repeat(rating);
            createInAppNotification(vendor, NotificationType.NEW_REVIEW,
                    "New Review",
                    reviewerName + " left a " + rating + "-star review " + stars + " on your " + reviewType,
                    RelatedEntityType.REVIEW, null);

            log.info("New review notification sent to vendor: {}", vendorPublicId);
        } catch (Exception e) {
            log.error("Failed to send new review notification to vendor: {}", vendorPublicId, e);
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    @Transactional
    public void notifyVendorFavorited(String vendorPublicId, String customerName) {
        try {
            User vendor = resolveUser(vendorPublicId);

            createInAppNotification(vendor, NotificationType.NEW_FAVOURITE,
                    "New Favorite",
                    customerName + " added your restaurant to their favorites! ❤️",
                    RelatedEntityType.USER, vendorPublicId);

            log.info("Vendor favorited notification sent to vendor: {}", vendorPublicId);
        } catch (Exception e) {
            log.error("Failed to send vendor favorited notification to vendor: {}", vendorPublicId, e);
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    // ========== BROADCAST ==========

    /**
     * Producer side — called directly from the admin controller. Just writes a
     * BROADCAST_SENT outbox event in the same transaction as the request and
     * returns; the actual fan-out happens in {@link #processBroadcast} once
     * OutboxPoller publishes the event to Kafka and NotificationEventConsumer
     * picks it back up. This is the same producer/consumer split every other
     * notification-triggering event in the app already uses — broadcasts used
     * to be the one exception (a plain @Async method with no retry and no
     * failure visibility), which is what let a broadcast silently fail while
     * still reporting "sent successfully" to the admin.
     */
    @Transactional
    public void enqueueBroadcast(BroadcastNotificationRequestDto dto, String sentBy) {
        outboxEventService.broadcastSent(
                dto.getTitle(),
                dto.getMessage(),
                dto.getType().name(),
                dto.getTargetAudience().name(),
                sentBy);
    }

    /**
     * Consumer side — invoked by {@link NotificationEventConsumer} for a
     * BROADCAST_SENT event. Fans the notification out to every user in the
     * target audience, in batches, then records the BroadcastLog entry that
     * powers the admin History tab. If this throws, the Kafka message is not
     * acknowledged and the event is retried like any other outbox event
     * (up to 3 attempts before OutboxPoller marks it FAILED) — unlike the old
     * @Async version, a mid-batch failure here is neither silent nor terminal.
     */
    @Transactional
    public void processBroadcast(String title, String message, String notificationType,
                                 String targetAudience, String sentBy) {
        NotificationType type = NotificationType.valueOf(notificationType);
        BroadcastNotificationRequestDto.TargetAudience audience =
                BroadcastNotificationRequestDto.TargetAudience.valueOf(targetAudience);
        final int batchSize = 500;

        long recipientCount = switch (audience) {
            case CUSTOMERS -> userRepository.countByRole(Role.CUSTOMER);
            case VENDORS   -> userRepository.countByRole(Role.VENDOR);
            case ALL       -> userRepository.count();
        };

        Pageable pageable = PageRequest.of(0, batchSize);
        while (true) {
            Page<User> page = switch (audience) {
                case CUSTOMERS -> userRepository.findAllByRole(Role.CUSTOMER, pageable);
                case VENDORS   -> userRepository.findAllByRole(Role.VENDOR, pageable);
                case ALL       -> userRepository.findAll(pageable);
            };

            if (page.isEmpty()) {
                break;
            }

            // Respect the same opt-out every other customer-facing notification in
            // this class honors (areNotificationsEnabled only applies to customers —
            // vendors/admins are always considered enabled). Filtered out before
            // building either the in-app row or the email, so an opted-out customer
            // gets neither, consistent with the rest of the app.
            List<User> eligible = page.getContent().stream()
                    .filter(this::areNotificationsEnabled)
                    .toList();

            List<Notification> notifications = eligible.stream()
                    .map(user -> Notification.builder()
                            .user(user)
                            .title(title)
                            .message(message)
                            .type(type)
                            .relatedEntityType(null)
                            .relatedEntityId(null)
                            .createdAt(LocalDateTime.now())
                            .isRead(false)
                            .build())
                    .toList();

            notificationRepository.saveAll(notifications);

            // Email — best-effort per recipient. Unlike the single-recipient notify
            // methods (where an email failure legitimately voids the whole thing and
            // is allowed to throw/retry), a broadcast fans out to many people at
            // once: one bad or bouncing address must not abort in-app delivery to
            // everyone else, and must not repeatedly retry the entire batch just
            // because of one address every other recipient already got.
            for (User user : eligible) {
                try {
                    emailService.sendNotificationEmail(user.getEmail(), user.getFirstName(), title, message);
                } catch (Exception e) {
                    log.warn("broadcast.email_failed userPublicId={} email={}",
                            user.getPublicUserId(), user.getEmail(), e);
                }
            }

            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        broadcastLogRepository.save(BroadcastLog.builder()
                .title(title)
                .message(message)
                .type(type)
                .targetAudience(audience.name())
                .recipientCount((int) recipientCount)
                .sentAt(LocalDateTime.now())
                .sentBy(sentBy)
                .build());

        log.info("Broadcast notification sent to {} recipient(s) [audience={}]: [{}] {}",
                recipientCount, audience, type, title);
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
        notificationRepository.markAllAsReadByUser(user, LocalDateTime.now());
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

    /**
     * Notify the vendor that a customer cancelled an order the vendor had already accepted.
     * Channels: In-App only (email not warranted for operational alerts like this).
     */
    @Transactional
    public void notifyVendorCustomerCancelled(String publicOrderId) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            User vendorUser = order.getVendor().getUser();
            if (vendorUser == null || !areNotificationsEnabled(vendorUser)) return;

            String customerName = order.getCustomer().getUser().getFirstName()
                    + " " + order.getCustomer().getUser().getLastName();

            createInAppNotification(vendorUser, NotificationType.ORDER_UPDATE,
                    "Order Cancelled by Customer",
                    "Customer " + customerName + " has cancelled order #" + publicOrderId
                    + ". Any preparation already started should be stopped.",
                    RelatedEntityType.ORDER, publicOrderId);

            log.info("Vendor notified of customer cancellation for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to notify vendor of customer cancellation for order: {}", publicOrderId, e);
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    /**
     * Notify the vendor and all admins that a CONFIRMED/PREPARING order has run past
     * its fulfillmentDeadline without being moved forward (ready/out-for-delivery) or
     * explicitly cancelled.
     *
     * This is a heads-up, not a cancellation — the vendor still has time to act (mark
     * it ready, or call "unable to fulfil" if they genuinely can't complete it) before
     * {@link com.afrochow.order.service.OrderFulfillmentOverdueScheduler}'s second pass
     * auto-cancels and refunds it.
     *
     * Channels: In-App only (operational alert, not customer-facing).
     */
    @Transactional
    public void notifyVendorAndAdminsOrderOverdue(String publicOrderId) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            String restaurantName = order.getVendor().getRestaurantName();

            User vendorUser = order.getVendor().getUser();
            if (vendorUser != null && areNotificationsEnabled(vendorUser)) {
                createInAppNotification(vendorUser, NotificationType.ORDER_UPDATE,
                        "Order Running Late",
                        "Order #" + publicOrderId + " is past its expected ready time and still needs "
                        + "action. Please update its status, or contact Afrochow support if you can't "
                        + "fulfil it — otherwise it will be automatically cancelled and refunded.",
                        RelatedEntityType.ORDER, publicOrderId);
            }

            List<User> admins = new ArrayList<>(userRepository.findByRoleAndIsActive(Role.ADMIN, true));
            admins.addAll(userRepository.findByRoleAndIsActive(Role.SUPERADMIN, true));
            for (User admin : admins) {
                createInAppNotification(admin, NotificationType.SYSTEM_ALERT,
                        "Order Overdue — Vendor Unresponsive",
                        "Order #" + publicOrderId + " from " + restaurantName
                        + " is past its expected ready time and still CONFIRMED/PREPARING. "
                        + "It will be auto-cancelled and refunded if still unresolved.",
                        RelatedEntityType.ORDER, publicOrderId);
            }

            log.info("Vendor and {} admin(s) notified of overdue order: {}", admins.size(), publicOrderId);
        } catch (Exception e) {
            log.error("Failed to notify vendor/admins of overdue order: {}", publicOrderId, e);
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    /**
     * Notify the customer that the vendor cancelled an order they had already accepted.
     *
     * This is distinct from a plain ORDER_CANCELLED notification because:
     *  - The payment was already CAPTURED (not just authorised), so the customer's card
     *    was actually charged. The message must say "refund" not "hold release".
     *  - The vendor's reason is shown to help the customer understand what happened.
     *
     * Channels: In-App + Email (because real money was taken and is being returned).
     */
    @Transactional
    public void notifyCustomerVendorUnableToFulfil(String publicOrderId, String reason) {
        try {
            Order order = loadOrder(publicOrderId);
            if (order == null) return;

            User customer = order.getCustomer().getUser();
            if (!areNotificationsEnabled(customer)) return;

            String vendorName = order.getVendor().getRestaurantName();
            String reasonSuffix = (reason != null && !reason.isBlank())
                    ? " Reason: " + reason
                    : "";

            String title   = "Order Cancelled by Restaurant";
            String message = "We're sorry — " + vendorName + " is unable to fulfil your order." + reasonSuffix
                    + " A full refund has been issued and should appear on your statement within 5–10 business days."
                    + " We apologise for the inconvenience.";

            createInAppNotification(customer, NotificationType.ORDER_UPDATE,
                    title, message, RelatedEntityType.ORDER, publicOrderId);

            // Email: customer paid real money so a confirmation of the refund is warranted
            try {
                emailService.sendNotificationEmail(
                        customer.getEmail(),
                        customer.getFirstName(),
                        title,
                        message);
            } catch (Exception emailEx) {
                log.warn("notifyCustomerVendorUnableToFulfil — email failed for order {} ({}): {}",
                        publicOrderId, customer.getEmail(), emailEx.getMessage());
                throw new IllegalStateException("Notification email dispatch failed", emailEx);
            }

            log.info("Customer notified of vendor unable-to-fulfil for order: {}", publicOrderId);
        } catch (Exception e) {
            log.error("Failed to notify customer of vendor unable-to-fulfil for order: {}", publicOrderId, e);
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    // ========== AUTH / ACCOUNT LIFECYCLE ==========

    @Transactional
    public void notifyUserRegistered(String publicUserId, String email,
                                     String firstName, String role) {
        try {
            emailService.sendWelcomeEmail(email, firstName, role);
        } catch (Exception e) {
            log.error("notifyUserRegistered — welcome email failed for {} ({}): {}", email, publicUserId, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
        String message = switch (role) {
            case "VENDOR" -> "Welcome to Afrochow! Your vendor account is now active. You can start adding your products and manage orders.";
            case "ADMIN"  -> "Welcome to Afrochow Admin! Your admin account is now active.";
            default       -> "Welcome to Afrochow! Your account is now verified. Explore our amazing African cuisine!";
        };
        createNotification(publicUserId, "Welcome to Afrochow!", message,
                NotificationType.SYSTEM_ALERT, null, null);
    }

    @Transactional
    public void notifyPasswordChanged(String publicUserId, String email, String firstName) {
        try {
            emailService.sendPasswordChangedEmail(email, firstName);
        } catch (Exception e) {
            log.error("notifyPasswordChanged — email failed for {} ({}): {}", email, publicUserId, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
        createNotification(publicUserId,
                "Password Changed Successfully",
                "Your password has been changed. If you did not make this change, contact support immediately.",
                NotificationType.SYSTEM_ALERT, null, null);
    }

    @Transactional
    public void notifyPasswordResetRequested(String publicUserId, String email,
                                             String firstName, String resetLink) {
        try {
            emailService.sendPasswordResetEmail(email, firstName, resetLink);
        } catch (Exception e) {
            log.error("notifyPasswordResetRequested — email failed for {} ({}): {}", email, publicUserId, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
        createNotification(publicUserId,
                "Password Reset Requested",
                "A password reset was requested for your account. If you did not request this, please secure your account immediately.",
                NotificationType.SYSTEM_ALERT, null, null);
    }

    public void notifyEmailVerificationSent(String publicUserId, String email,
                                            String firstName, String verificationToken) {
        try {
            emailService.sendEmailVerificationEmail(email, verificationToken, firstName);
        } catch (Exception e) {
            log.error("notifyEmailVerificationSent — email failed for {} ({}): {}", email, publicUserId, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    public void notifyAccountDeletionRequested(String publicUserId, String email, String firstName) {
        String title = "Account Deletion Requested";
        String message = "Your account has been deactivated. You have 30 days to reactivate it by signing back in. " +
                "After that, your profile, addresses, order history and reviews are permanently removed. " +
                "If you did not request this, please contact our support team immediately.";

        try {
            emailService.sendNotificationEmail(email, firstName, title, message);
        } catch (Exception e) {
            log.error("notifyAccountDeletionRequested — email failed for {} ({}): {}", email, publicUserId, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    // ========== WAITLIST ==========

    /**
     * Confirms a waitlist join/update. No User account exists yet at this point,
     * so — like notifyEmailVerificationSent — this is email-only, no in-app
     * Notification row.
     */
    public void notifyWaitlistJoined(String email, String name, String role) {
        String title = "You're on the waitlist!";
        String message = "VENDOR".equals(role)
                ? "Thanks for your interest in selling on Afrochow! We've added you to the vendor waitlist "
                        + "and will reach out as soon as registrations open in your area."
                : "Thanks for joining the Afrochow waitlist! We'll let you know as soon as we launch near you.";

        try {
            emailService.sendNotificationEmail(email, name, title, message);
        } catch (Exception e) {
            log.error("notifyWaitlistJoined — email failed for {}: {}", email, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    // ========== VENDOR ADMIN LIFECYCLE ==========

    public void notifyVendorProvisional(String email, String firstName, String restaurantName) {
        try {
            emailService.sendVendorProvisionalApprovalEmail(email, firstName, restaurantName);
        } catch (Exception e) {
            log.error("notifyVendorProvisional — email failed for {}: {}", email, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    @Transactional
    public void notifyAdminsVendorCertificateUploaded(String publicVendorId,
                                                      String publicUserId,
                                                      String restaurantName,
                                                      String certificateUrl) {
        List<User> admins = new ArrayList<>(userRepository.findByRoleAndIsActive(Role.ADMIN, true));
        admins.addAll(userRepository.findByRoleAndIsActive(Role.SUPERADMIN, true));

        String safeRestaurantName = restaurantName != null && !restaurantName.isBlank()
                ? restaurantName
                : "A vendor";
        String message = safeRestaurantName + " uploaded a food handling certificate for review.";

        for (User admin : admins) {
            createInAppNotification(admin,
                    NotificationType.SYSTEM_ALERT,
                    "Vendor Certificate Review Needed",
                    message,
                    RelatedEntityType.VENDOR,
                    publicVendorId != null && !publicVendorId.isBlank() ? publicVendorId : publicUserId);
        }

        log.info("Admins notified for vendor certificate upload vendor={} adminCount={} certUrlPresent={}",
                publicVendorId, admins.size(), certificateUrl != null && !certificateUrl.isBlank());
    }

    /**
     * Alerts admins that a charge has been disputed (chargeback).
     *
     * <p>Stripe debits the disputed amount from the platform balance the moment the
     * dispute opens, and the vendor may already have been paid out for the order, so
     * this needs a human looking at it promptly — evidence is due on a Stripe-imposed
     * deadline and an unanswered dispute is lost by default.
     */
    @Transactional
    public void notifyAdminsPaymentDisputed(String publicOrderId, BigDecimal amount, String reason) {
        List<User> admins = new ArrayList<>(userRepository.findByRoleAndIsActive(Role.ADMIN, true));
        admins.addAll(userRepository.findByRoleAndIsActive(Role.SUPERADMIN, true));

        String message = String.format(
                "A customer disputed the payment for order #%s (CA$%.2f, reason: %s). " +
                        "Respond in the Stripe dashboard before the evidence deadline — " +
                        "unanswered disputes are lost automatically.",
                publicOrderId, amount, reason);

        for (User admin : admins) {
            createInAppNotification(admin,
                    NotificationType.SYSTEM_ALERT,
                    "Payment Disputed",
                    message,
                    RelatedEntityType.ORDER,
                    publicOrderId);
        }

        log.warn("payment.dispute.admins_notified publicOrderId={} amount={} reason={} adminCount={}",
                publicOrderId, amount, reason, admins.size());
    }

    public void notifyVendorApproved(String email, String firstName, String restaurantName) {
        try {
            emailService.sendVendorApprovalEmail(email, firstName, restaurantName);
        } catch (Exception e) {
            log.error("notifyVendorApproved — email failed for {}: {}", email, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    public void notifyVendorRejected(String email, String firstName,
                                     String restaurantName, String reason) {
        try {
            emailService.sendVendorRejectionEmail(email, firstName, restaurantName, reason);
        } catch (Exception e) {
            log.error("notifyVendorRejected — email failed for {}: {}", email, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    public void notifyVendorSuspended(String email, String firstName, String restaurantName) {
        try {
            emailService.sendVendorSuspensionEmail(email, firstName, restaurantName);
        } catch (Exception e) {
            log.error("notifyVendorSuspended — email failed for {}: {}", email, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
    }

    public void notifyVendorReinstated(String email, String firstName, String restaurantName) {
        try {
            emailService.sendVendorReinstatementEmail(email, firstName, restaurantName);
        } catch (Exception e) {
            log.error("notifyVendorReinstated — email failed for {}: {}", email, e.getMessage());
            throw new IllegalStateException("Notification dispatch failed", e);
        }
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
