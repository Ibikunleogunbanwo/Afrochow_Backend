package com.afrochow.outbox.service;

import com.afrochow.notification.service.NotificationService;
import com.afrochow.outbox.enums.OutboxStatus;
import com.afrochow.outbox.model.OutboxEvent;
import com.afrochow.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Read / dispatch side of the transactional outbox.
 *
 * Polls every 2 seconds for PENDING events, marks them PROCESSING (using
 * SELECT FOR UPDATE SKIP LOCKED so concurrent instances don't double-dispatch),
 * calls the appropriate NotificationService method, then marks them PROCESSED.
 *
 * Failed dispatches are retried up to MAX_RETRIES times; after that the row
 * moves to FAILED status for manual inspection.
 *
 * The poll runs in its own transaction so that:
 *  1. The status update to PROCESSING is committed before dispatch starts —
 *     another poller instance won't pick up the same row.
 *  2. The PROCESSED/FAILED update is committed after dispatch, regardless of
 *     whether the notification itself threw.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private static final int BATCH_SIZE  = 50;
    private static final int MAX_RETRIES = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationService   notificationService;
    private final ObjectMapper          objectMapper;

    @Scheduled(fixedDelay = 2000)   // poll every 2 s — tune to your traffic
    @Transactional
    public void poll() {
        List<OutboxEvent> events = outboxEventRepository
                .findAndLockPendingEvents(PageRequest.of(0, BATCH_SIZE));

        if (events.isEmpty()) return;

        log.debug("outbox.poll batch={}", events.size());

        for (OutboxEvent event : events) {
            // Claim: mark PROCESSING so no other poller touches this row
            event.setStatus(OutboxStatus.PROCESSING);
            outboxEventRepository.save(event);

            try {
                dispatch(event);
                event.setStatus(OutboxStatus.PROCESSED);
                event.setProcessedAt(LocalDateTime.now());
                log.debug("outbox.processed id={} type={}", event.getId(), event.getEventType());
            } catch (Exception ex) {
                int retries = event.getRetryCount() + 1;
                event.setRetryCount(retries);
                event.setLastError(truncate(ex.getMessage(), 500));

                if (retries >= MAX_RETRIES) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("outbox.failed id={} type={} retries={} — moved to FAILED",
                            event.getId(), event.getEventType(), retries, ex);
                } else {
                    event.setStatus(OutboxStatus.PENDING);
                    log.warn("outbox.retry id={} type={} retries={}/{}",
                            event.getId(), event.getEventType(), retries, MAX_RETRIES, ex);
                }
            }

            outboxEventRepository.save(event);
        }
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    private void dispatch(OutboxEvent event) throws Exception {
        Map<String, String> p = parse(event.getPayload());

        switch (event.getEventType()) {

            case ORDER_PLACED ->
                    notificationService.notifyVendorNewOrder(p.get("publicOrderId"));

            case CUSTOMER_ORDER_RECEIVED ->
                    notificationService.notifyCustomerOrderReceived(p.get("publicOrderId"));

            case ORDER_CONFIRMED ->
                    notificationService.notifyCustomerOrderConfirmed(p.get("publicOrderId"));

            case ORDER_CANCELLED ->
                    notificationService.notifyCustomerOrderCancelled(
                            p.get("publicOrderId"),
                            p.get("reason"),
                            p.get("previousStatus"));

            case ORDER_PREPARING ->
                    notificationService.notifyCustomerOrderPreparing(p.get("publicOrderId"));

            case ORDER_READY ->
                    notificationService.notifyCustomerOrderReady(p.get("publicOrderId"));

            case ORDER_OUT_FOR_DELIVERY ->
                    notificationService.notifyCustomerOrderOutForDelivery(p.get("publicOrderId"));

            case ORDER_DELIVERED ->
                    notificationService.notifyCustomerOrderDelivered(p.get("publicOrderId"));

            case PAYMENT_CAPTURED ->
                    notificationService.notifyPaymentSuccess(
                            p.get("userPublicId"),
                            p.get("paymentId"),
                            p.get("publicOrderId"),
                            new BigDecimal(p.get("amount")));

            case PAYMENT_FAILED ->
                    notificationService.notifyPaymentFailed(
                            p.get("userPublicId"),
                            p.get("publicOrderId"),
                            p.get("reason"));

            case VENDOR_REVIEWED ->
                    notificationService.notifyVendorNewReview(
                            p.get("vendorPublicId"),
                            p.get("reviewerName"),
                            Integer.parseInt(p.get("rating")),
                            p.get("reviewType"));

            case VENDOR_FAVOURITED ->
                    notificationService.notifyVendorFavorited(
                            p.get("vendorPublicId"),
                            p.get("customerName"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> parse(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
