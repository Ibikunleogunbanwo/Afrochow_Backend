package com.afrochow.outbox.service;

import com.afrochow.outbox.enums.OutboxEventType;
import com.afrochow.outbox.model.OutboxEvent;
import com.afrochow.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Write side of the transactional outbox.
 *
 * All methods MUST be called within an existing @Transactional context (i.e. from
 * OrderService, PaymentService, etc.) so the outbox row is committed atomically with
 * the business state change.  Propagation.MANDATORY enforces this at runtime.
 *
 * Do NOT call these methods outside a transaction — if the row is saved but the
 * parent transaction rolls back, the outbox entry rolls back with it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    /**
     * ObjectMapper is thread-safe after construction and has no Spring-managed
     * lifecycle, so we hold a private static instance rather than injecting it.
     * This avoids any bean-resolution ordering issues during application startup.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OutboxEventRepository outboxEventRepository;

    @Value("${app.kafka.topics.domain-events:afrochow.domain-events}")
    private String domainEventsTopic;

    // ── Order lifecycle ──────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderPlaced(String publicOrderId) {
        saveOrderEvent(OutboxEventType.ORDER_PLACED, publicOrderId, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void customerOrderReceived(String publicOrderId) {
        saveOrderEvent(OutboxEventType.CUSTOMER_ORDER_RECEIVED, publicOrderId, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderConfirmed(String publicOrderId) {
        saveOrderEvent(OutboxEventType.ORDER_CONFIRMED, publicOrderId, Map.of("publicOrderId", publicOrderId));
    }

    /**
     * @param cancelledBy  Who initiated the cancellation: CUSTOMER | VENDOR | ADMIN | SYSTEM
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void orderCancelled(String publicOrderId, String reason,
                               String previousStatus, String cancelledBy) {
        saveOrderEvent(OutboxEventType.ORDER_CANCELLED, publicOrderId, Map.of(
                "publicOrderId",  publicOrderId,
                "reason",         reason != null ? reason : "",
                "previousStatus", previousStatus,
                "cancelledBy",    cancelledBy
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderPreparing(String publicOrderId) {
        saveOrderEvent(OutboxEventType.ORDER_PREPARING, publicOrderId, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderReady(String publicOrderId) {
        saveOrderEvent(OutboxEventType.ORDER_READY, publicOrderId, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderOutForDelivery(String publicOrderId) {
        saveOrderEvent(OutboxEventType.ORDER_OUT_FOR_DELIVERY, publicOrderId, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderDelivered(String publicOrderId) {
        saveOrderEvent(OutboxEventType.ORDER_DELIVERED, publicOrderId, Map.of("publicOrderId", publicOrderId));
    }

    // ── Payment ──────────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentCaptured(String userPublicId, String paymentId,
                                String publicOrderId, BigDecimal amount) {
        savePaymentEvent(OutboxEventType.PAYMENT_CAPTURED, paymentId, Map.of(
                "userPublicId",  userPublicId,
                "paymentId",     paymentId,
                "publicOrderId", publicOrderId,
                "amount",        amount.toPlainString()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentFailed(String userPublicId, String publicOrderId, String reason) {
        saveOrderEvent(OutboxEventType.PAYMENT_FAILED, publicOrderId, Map.of(
                "userPublicId",  userPublicId,
                "publicOrderId", publicOrderId,
                "reason",        reason != null ? reason : "Unknown error"
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentTransferRequested(String publicOrderId) {
        saveOrderEvent(OutboxEventType.PAYMENT_TRANSFER_REQUESTED, publicOrderId, Map.of(
                "publicOrderId", publicOrderId
        ));
    }

    // ── Engagement ───────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorReviewed(String vendorPublicId, String reviewerName,
                               Integer rating, String reviewType) {
        saveVendorEvent(OutboxEventType.VENDOR_REVIEWED, vendorPublicId, Map.of(
                "vendorPublicId", vendorPublicId,
                "reviewerName",   reviewerName,
                "rating",         String.valueOf(rating),
                "reviewType",     reviewType
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorFavourited(String vendorPublicId, String customerName) {
        saveVendorEvent(OutboxEventType.VENDOR_FAVOURITED, vendorPublicId, Map.of(
                "vendorPublicId", vendorPublicId,
                "customerName",   customerName
        ));
    }

    /**
     * Fired when a customer cancels an order that the vendor has already accepted (CONFIRMED).
     * The vendor must be notified so they can stop any prep work in progress.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorCustomerCancelled(String publicOrderId) {
        saveOrderEvent(OutboxEventType.VENDOR_CUSTOMER_CANCELLED, publicOrderId, Map.of("publicOrderId", publicOrderId));
    }

    /**
     * Fired when a vendor cancels an order they had already accepted (CONFIRMED or PREPARING).
     * Unlike a rejection (PENDING → CANCELLED), the payment was already captured at this point,
     * so a real Stripe refund is issued and the notification messaging must reflect that.
     *
     * @param publicOrderId  the affected order
     * @param reason         vendor-provided explanation shown to the customer
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorUnableToFulfil(String publicOrderId, String reason) {
        saveOrderEvent(OutboxEventType.VENDOR_UNABLE_TO_FULFIL, publicOrderId, Map.of(
                "publicOrderId", publicOrderId,
                "reason",        reason != null ? reason : ""
        ));
    }

    // ── Auth / account lifecycle ─────────────────────────────────────────────

    @Transactional(propagation = Propagation.MANDATORY)
    public void userRegistered(String publicUserId, String email, String firstName, String role) {
        saveUserEvent(OutboxEventType.USER_REGISTERED, publicUserId, Map.of(
                "publicUserId", publicUserId,
                "email",        email,
                "firstName",    firstName,
                "role",         role
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void passwordChanged(String publicUserId, String email, String firstName) {
        saveUserEvent(OutboxEventType.PASSWORD_CHANGED, publicUserId, Map.of(
                "publicUserId", publicUserId,
                "email",        email,
                "firstName",    firstName
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void passwordResetRequested(String publicUserId, String email,
                                       String firstName, String resetLink) {
        saveUserEvent(OutboxEventType.PASSWORD_RESET_REQUESTED, publicUserId, Map.of(
                "publicUserId", publicUserId,
                "email",        email,
                "firstName",    firstName,
                "resetLink",    resetLink
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void emailVerificationSent(String publicUserId, String email,
                                      String firstName, String verificationToken) {
        saveUserEvent(OutboxEventType.EMAIL_VERIFICATION_SENT, publicUserId, Map.of(
                "publicUserId",      publicUserId,
                "email",             email,
                "firstName",         firstName,
                "verificationToken", verificationToken
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accountDeletionRequested(String publicUserId, String email, String firstName) {
        saveUserEvent(OutboxEventType.ACCOUNT_DELETION_REQUESTED, publicUserId, Map.of(
                "publicUserId", publicUserId,
                "email",        email,
                "firstName",    firstName
        ));
    }

    // ── Address lifecycle ───────────────────────────────────────────────────

    @Transactional(propagation = Propagation.MANDATORY)
    public void addressGeocodingRequested(String publicAddressId) {
        save(OutboxEventType.ADDRESS_GEOCODING_REQUESTED, "ADDRESS", publicAddressId, Map.of(
                "publicAddressId", publicAddressId
        ));
    }

    // ── Vendor admin lifecycle ────────────────────────────────────────────────

    /** Fires when a vendor is provisionally approved — live but cert still required. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorProvisional(String publicUserId, String email,
                                  String firstName, String restaurantName) {
        saveUserEvent(OutboxEventType.VENDOR_PROVISIONAL, publicUserId, Map.of(
                "publicUserId",   publicUserId,
                "email",          email,
                "firstName",      firstName,
                "restaurantName", restaurantName
        ));
    }

    /** Fires when a vendor is fully verified (cert confirmed → VERIFIED). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorApproved(String publicUserId, String email,
                               String firstName, String restaurantName) {
        saveUserEvent(OutboxEventType.VENDOR_APPROVED, publicUserId, Map.of(
                "publicUserId",  publicUserId,
                "email",         email,
                "firstName",     firstName,
                "restaurantName", restaurantName
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorRejected(String publicUserId, String email, String firstName,
                               String restaurantName, String reason) {
        saveUserEvent(OutboxEventType.VENDOR_REJECTED, publicUserId, Map.of(
                "publicUserId",   publicUserId,
                "email",          email,
                "firstName",      firstName,
                "restaurantName", restaurantName,
                "reason",         reason != null ? reason : ""
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorSuspended(String publicUserId, String email,
                                String firstName, String restaurantName) {
        saveUserEvent(OutboxEventType.VENDOR_SUSPENDED, publicUserId, Map.of(
                "publicUserId",   publicUserId,
                "email",          email,
                "firstName",      firstName,
                "restaurantName", restaurantName
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorReinstated(String publicUserId, String email,
                                 String firstName, String restaurantName) {
        saveUserEvent(OutboxEventType.VENDOR_REINSTATED, publicUserId, Map.of(
                "publicUserId",   publicUserId,
                "email",          email,
                "firstName",      firstName,
                "restaurantName", restaurantName
        ));
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void saveOrderEvent(OutboxEventType type, String publicOrderId, Map<String, String> payload) {
        save(type, "ORDER", publicOrderId, payload);
    }

    private void savePaymentEvent(OutboxEventType type, String paymentId, Map<String, String> payload) {
        save(type, "PAYMENT", paymentId, payload);
    }

    private void saveUserEvent(OutboxEventType type, String publicUserId, Map<String, String> payload) {
        save(type, "USER", publicUserId, payload);
    }

    private void saveVendorEvent(OutboxEventType type, String publicVendorId, Map<String, String> payload) {
        save(type, "VENDOR", publicVendorId, payload);
    }

    private void save(OutboxEventType type, String aggregateType,
                      String aggregateId, Map<String, String> payload) {
        try {
            String json = MAPPER.writeValueAsString(payload);
            outboxEventRepository.save(OutboxEvent.builder()
                    .eventType(type)
                    .topic(domainEventsTopic)
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .payload(json)
                    .build());
            log.debug("outbox.saved type={} aggregateType={} aggregateId={}",
                    type, aggregateType, aggregateId);
        } catch (JsonProcessingException e) {
            // Should never happen for Map<String,String> — rethrow to roll back the parent tx
            throw new IllegalStateException("Failed to serialize outbox payload for " + type, e);
        }
    }
}
