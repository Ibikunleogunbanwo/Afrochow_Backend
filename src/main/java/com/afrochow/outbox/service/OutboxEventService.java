package com.afrochow.outbox.service;

import com.afrochow.outbox.enums.OutboxEventType;
import com.afrochow.outbox.model.OutboxEvent;
import com.afrochow.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // ── Order lifecycle ──────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderPlaced(String publicOrderId) {
        save(OutboxEventType.ORDER_PLACED, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void customerOrderReceived(String publicOrderId) {
        save(OutboxEventType.CUSTOMER_ORDER_RECEIVED, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderConfirmed(String publicOrderId) {
        save(OutboxEventType.ORDER_CONFIRMED, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderCancelled(String publicOrderId, String reason, String previousStatus) {
        save(OutboxEventType.ORDER_CANCELLED, Map.of(
                "publicOrderId",  publicOrderId,
                "reason",         reason,
                "previousStatus", previousStatus
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderPreparing(String publicOrderId) {
        save(OutboxEventType.ORDER_PREPARING, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderReady(String publicOrderId) {
        save(OutboxEventType.ORDER_READY, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderOutForDelivery(String publicOrderId) {
        save(OutboxEventType.ORDER_OUT_FOR_DELIVERY, Map.of("publicOrderId", publicOrderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderDelivered(String publicOrderId) {
        save(OutboxEventType.ORDER_DELIVERED, Map.of("publicOrderId", publicOrderId));
    }

    // ── Payment ──────────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentCaptured(String userPublicId, String paymentId,
                                String publicOrderId, BigDecimal amount) {
        save(OutboxEventType.PAYMENT_CAPTURED, Map.of(
                "userPublicId",  userPublicId,
                "paymentId",     paymentId,
                "publicOrderId", publicOrderId,
                "amount",        amount.toPlainString()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentFailed(String userPublicId, String publicOrderId, String reason) {
        save(OutboxEventType.PAYMENT_FAILED, Map.of(
                "userPublicId",  userPublicId,
                "publicOrderId", publicOrderId,
                "reason",        reason != null ? reason : "Unknown error"
        ));
    }

    // ── Engagement ───────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorReviewed(String vendorPublicId, String reviewerName,
                               Integer rating, String reviewType) {
        save(OutboxEventType.VENDOR_REVIEWED, Map.of(
                "vendorPublicId", vendorPublicId,
                "reviewerName",   reviewerName,
                "rating",         String.valueOf(rating),
                "reviewType",     reviewType
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void vendorFavourited(String vendorPublicId, String customerName) {
        save(OutboxEventType.VENDOR_FAVOURITED, Map.of(
                "vendorPublicId", vendorPublicId,
                "customerName",   customerName
        ));
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void save(OutboxEventType type, Map<String, String> payload) {
        try {
            String json = MAPPER.writeValueAsString(payload);
            outboxEventRepository.save(OutboxEvent.builder()
                    .eventType(type)
                    .payload(json)
                    .build());
            log.debug("outbox.saved type={}", type);
        } catch (JsonProcessingException e) {
            // Should never happen for Map<String,String> — rethrow to roll back the parent tx
            throw new IllegalStateException("Failed to serialize outbox payload for " + type, e);
        }
    }
}
