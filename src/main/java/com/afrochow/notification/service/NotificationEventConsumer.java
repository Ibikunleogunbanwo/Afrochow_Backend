package com.afrochow.notification.service;

import com.afrochow.kafka.service.ProcessedKafkaEventService;
import com.afrochow.outbox.enums.OutboxEventType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Consumes domain events published by the transactional outbox and performs the
 * notification side effects that previously lived inside OutboxPoller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.kafka.consumers.notifications.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationEventConsumer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<OutboxEventType> SUPPORTED_TYPES = EnumSet.of(
            OutboxEventType.ORDER_PLACED,
            OutboxEventType.CUSTOMER_ORDER_RECEIVED,
            OutboxEventType.ORDER_CONFIRMED,
            OutboxEventType.ORDER_CANCELLED,
            OutboxEventType.ORDER_PREPARING,
            OutboxEventType.ORDER_READY,
            OutboxEventType.ORDER_OUT_FOR_DELIVERY,
            OutboxEventType.ORDER_DELIVERED,
            OutboxEventType.PAYMENT_CAPTURED,
            OutboxEventType.PAYMENT_FAILED,
            OutboxEventType.VENDOR_REVIEWED,
            OutboxEventType.VENDOR_FAVOURITED,
            OutboxEventType.VENDOR_CUSTOMER_CANCELLED,
            OutboxEventType.VENDOR_UNABLE_TO_FULFIL,
            OutboxEventType.USER_REGISTERED,
            OutboxEventType.PASSWORD_CHANGED,
            OutboxEventType.PASSWORD_RESET_REQUESTED,
            OutboxEventType.EMAIL_VERIFICATION_SENT,
            OutboxEventType.ACCOUNT_DELETION_REQUESTED,
            OutboxEventType.VENDOR_PROVISIONAL,
            OutboxEventType.VENDOR_CERTIFICATE_UPLOADED,
            OutboxEventType.VENDOR_APPROVED,
            OutboxEventType.VENDOR_REJECTED,
            OutboxEventType.VENDOR_SUSPENDED,
            OutboxEventType.VENDOR_REINSTATED
    );

    private final NotificationService notificationService;
    private final ProcessedKafkaEventService processedKafkaEventService;

    @Value("${spring.kafka.consumer.group-id:afrochow-notification-service}")
    private String consumerName;

    @KafkaListener(
            topics = "${app.kafka.topics.domain-events:afrochow.domain-events}",
            groupId = "${spring.kafka.consumer.group-id:afrochow-notification-service}"
    )
    public void consume(
            @Payload String payload,
            @Header("outbox-id") String outboxId,
            @Header("outbox-event-id") String eventId,
            @Header("outbox-event-type") String eventType,
            Acknowledgment acknowledgment
    ) throws Exception {
        OutboxEventType type = OutboxEventType.valueOf(eventType);
        Map<String, String> p = parse(payload);

        if (!SUPPORTED_TYPES.contains(type)) {
            log.debug("notification.consumer.ignored outboxId={} eventId={} type={}",
                    outboxId, eventId, type);
            acknowledgment.acknowledge();
            return;
        }

        log.debug("notification.consumer.received outboxId={} eventId={} type={}",
                outboxId, eventId, type);

        if (processedKafkaEventService.alreadyProcessed(consumerName, eventId)) {
            log.info("notification.consumer.duplicate_skipped outboxId={} eventId={} type={}",
                    outboxId, eventId, type);
            acknowledgment.acknowledge();
            return;
        }

        dispatch(type, p, eventId);
        processedKafkaEventService.markProcessed(consumerName, eventId, outboxId, eventType);
        acknowledgment.acknowledge();

        log.debug("notification.consumer.acked outboxId={} eventId={} type={}",
                outboxId, eventId, type);
    }

    private void dispatch(OutboxEventType type, Map<String, String> p, String eventId) {
        switch (type) {

            case ORDER_PLACED ->
                    notificationService.notifyVendorNewOrder(required(p, "publicOrderId", type, eventId));

            case CUSTOMER_ORDER_RECEIVED ->
                    notificationService.notifyCustomerOrderReceived(required(p, "publicOrderId", type, eventId));

            case ORDER_CONFIRMED ->
                    notificationService.notifyCustomerOrderConfirmed(required(p, "publicOrderId", type, eventId));

            case ORDER_CANCELLED ->
                    notificationService.notifyCustomerOrderCancelled(
                            required(p, "publicOrderId", type, eventId),
                            p.get("reason"),
                            p.get("previousStatus"),
                            p.get("cancelledBy"));

            case ORDER_PREPARING ->
                    notificationService.notifyCustomerOrderPreparing(required(p, "publicOrderId", type, eventId));

            case ORDER_READY ->
                    notificationService.notifyCustomerOrderReady(required(p, "publicOrderId", type, eventId));

            case ORDER_OUT_FOR_DELIVERY ->
                    notificationService.notifyCustomerOrderOutForDelivery(required(p, "publicOrderId", type, eventId));

            case ORDER_DELIVERED ->
                    notificationService.notifyCustomerOrderDelivered(required(p, "publicOrderId", type, eventId));

            case PAYMENT_CAPTURED ->
                    notificationService.notifyPaymentSuccess(
                            required(p, "userPublicId", type, eventId),
                            required(p, "paymentId", type, eventId),
                            required(p, "publicOrderId", type, eventId),
                            new BigDecimal(required(p, "amount", type, eventId)));

            case PAYMENT_FAILED ->
                    notificationService.notifyPaymentFailed(
                            required(p, "userPublicId", type, eventId),
                            required(p, "publicOrderId", type, eventId),
                            p.get("reason"));

            case VENDOR_REVIEWED ->
                    notificationService.notifyVendorNewReview(
                            required(p, "vendorPublicId", type, eventId),
                            required(p, "reviewerName", type, eventId),
                            Integer.parseInt(required(p, "rating", type, eventId)),
                            required(p, "reviewType", type, eventId));

            case VENDOR_FAVOURITED ->
                    notificationService.notifyVendorFavorited(
                            required(p, "vendorPublicId", type, eventId),
                            required(p, "customerName", type, eventId));

            case VENDOR_CUSTOMER_CANCELLED ->
                    notificationService.notifyVendorCustomerCancelled(required(p, "publicOrderId", type, eventId));

            case VENDOR_UNABLE_TO_FULFIL ->
                    notificationService.notifyCustomerVendorUnableToFulfil(
                            required(p, "publicOrderId", type, eventId),
                            p.get("reason"));

            case USER_REGISTERED ->
                    notificationService.notifyUserRegistered(
                            required(p, "publicUserId", type, eventId),
                            required(p, "email", type, eventId),
                            required(p, "firstName", type, eventId),
                            required(p, "role", type, eventId));

            case PASSWORD_CHANGED ->
                    notificationService.notifyPasswordChanged(
                            required(p, "publicUserId", type, eventId),
                            required(p, "email", type, eventId),
                            required(p, "firstName", type, eventId));

            case PASSWORD_RESET_REQUESTED ->
                    notificationService.notifyPasswordResetRequested(
                            required(p, "publicUserId", type, eventId),
                            required(p, "email", type, eventId),
                            required(p, "firstName", type, eventId),
                            required(p, "resetLink", type, eventId));

            case EMAIL_VERIFICATION_SENT ->
                    notificationService.notifyEmailVerificationSent(
                            required(p, "publicUserId", type, eventId),
                            required(p, "email", type, eventId),
                            required(p, "firstName", type, eventId),
                            required(p, "verificationToken", type, eventId));

            case ACCOUNT_DELETION_REQUESTED ->
                    notificationService.notifyAccountDeletionRequested(
                            required(p, "publicUserId", type, eventId),
                            required(p, "email", type, eventId),
                            required(p, "firstName", type, eventId));

            case VENDOR_PROVISIONAL ->
                    notificationService.notifyVendorProvisional(
                            required(p, "email", type, eventId),
                            required(p, "firstName", type, eventId),
                            required(p, "restaurantName", type, eventId));

            case VENDOR_CERTIFICATE_UPLOADED ->
                    notificationService.notifyAdminsVendorCertificateUploaded(
                            required(p, "publicVendorId", type, eventId),
                            required(p, "publicUserId", type, eventId),
                            p.get("restaurantName"),
                            p.get("certificateUrl"));

            case VENDOR_APPROVED ->
                    notificationService.notifyVendorApproved(
                            required(p, "email", type, eventId),
                            required(p, "firstName", type, eventId),
                            required(p, "restaurantName", type, eventId));

            case VENDOR_REJECTED ->
                    notificationService.notifyVendorRejected(
                            required(p, "email", type, eventId),
                            required(p, "firstName", type, eventId),
                            required(p, "restaurantName", type, eventId),
                            p.get("reason"));

            case VENDOR_SUSPENDED ->
                    notificationService.notifyVendorSuspended(
                            required(p, "email", type, eventId),
                            required(p, "firstName", type, eventId),
                            required(p, "restaurantName", type, eventId));

            case VENDOR_REINSTATED ->
                    notificationService.notifyVendorReinstated(
                            required(p, "email", type, eventId),
                            required(p, "firstName", type, eventId),
                            required(p, "restaurantName", type, eventId));
        }
    }

    private Map<String, String> parse(String payload) throws Exception {
        return MAPPER.readValue(payload, new TypeReference<Map<String, String>>() {});
    }

    private String required(Map<String, String> payload, String key,
                            OutboxEventType type, String eventId) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing event payload field '" + key + "' for " + type
                            + " eventId=" + eventId);
        }
        return value;
    }
}
