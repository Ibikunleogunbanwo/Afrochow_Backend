package com.afrochow.order.service;

import com.afrochow.common.audit.OrderAuditLogger;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fulfillment Safety Net Scheduler — runs every 30 minutes.
 *
 * Catches two classes of orders that fell through the cracks:
 *
 * Pass 1 — Auto-Deliver:
 *   Orders still in OUT_FOR_DELIVERY or READY_FOR_PICKUP state whose
 *   requestedFulfillmentTime is older than 2 hours (the grace period).
 *   The system marks them as DELIVERED and captures payment automatically.
 *   This prevents Stripe's 7-day authorization hold from expiring on food
 *   that was actually delivered but never marked as such.
 *
 * Pass 2 — Retry Capture:
 *   Orders already in DELIVERED status whose payment is still AUTHORIZED
 *   (capture never completed — e.g. due to a momentary Stripe network error).
 *   The system retries the capture so Afrochow/vendors actually get paid.
 *
 * Distributed locking:
 *   ShedLock ensures only ONE instance acquires the lock at any given run.
 *   All other instances skip that cycle. The lock expires after 10 minutes
 *   so a crashed instance can never block the scheduler permanently.
 */
@Slf4j
@Service
public class FulfillmentSafetyNetScheduler {

    /** Grace period in hours before a past-due order is auto-delivered. */
    private static final int GRACE_PERIOD_HOURS = 2;

    private final OrderRepository  orderRepository;
    private final OrderService     orderService;
    private final OrderAuditLogger auditLogger;

    public FulfillmentSafetyNetScheduler(
            OrderRepository  orderRepository,
            OrderService     orderService,
            OrderAuditLogger auditLogger
    ) {
        this.orderRepository = orderRepository;
        this.orderService    = orderService;
        this.auditLogger     = auditLogger;
    }

    /**
     * Runs every 30 minutes. ShedLock limits execution to one instance at a time.
     * lockAtMostFor = 10 min (safety valve if the instance crashes mid-run).
     * lockAtLeastFor = 25 min (prevents rapid re-runs across instances).
     */
    @Scheduled(fixedDelayString = "${fulfillment.safety-net.interval-ms:1800000}")
    @SchedulerLock(
            name            = "FulfillmentSafetyNetScheduler",
            lockAtMostFor   = "PT10M",
            lockAtLeastFor  = "PT25M"
    )
    public void runSafetyNet() {
        log.info("SAFETY_NET_START — beginning fulfillment safety net run");

        LocalDateTime cutoff = LocalDateTime.now().minusHours(GRACE_PERIOD_HOURS);

        int autoDelivered  = 0;
        int captureRetried = 0;
        int errors         = 0;

        // ── Pass 1: Auto-deliver overdue orders ───────────────────────────────
        List<Order> overdueOrders = orderRepository.findOverdueActiveOrders(cutoff);
        log.info("SAFETY_NET_PASS1 — found {} overdue order(s) past {}h grace period", overdueOrders.size(), GRACE_PERIOD_HOURS);

        for (Order order : overdueOrders) {
            try {
                orderService.autoDeliverOrder(order);
                autoDelivered++;
                log.info("SAFETY_NET_AUTO_DELIVERED — orderId={} status={} fulfillmentTime={}",
                        order.getPublicOrderId(), order.getStatus(), order.getRequestedFulfillmentTime());
                auditLogger.logOrderTransition(
                        order.getPublicOrderId(),
                        order.getStatus().name(),
                        "DELIVERED",
                        "system:safety-net",
                        "Auto-delivered by fulfillment safety net — past grace period",
                        "COMPLETED",
                        null
                );
            } catch (Exception ex) {
                errors++;
                log.error("SAFETY_NET_ERROR — auto-deliver failed for orderId={} error={}",
                        order.getPublicOrderId(), ex.getMessage(), ex);
            }
        }

        // ── Pass 2: Retry failed captures ─────────────────────────────────────
        List<Order> uncapturedOrders = orderRepository.findDeliveredWithUnCapturedPayment();
        log.info("SAFETY_NET_PASS2 — found {} delivered order(s) with uncaptured payment", uncapturedOrders.size());

        for (Order order : uncapturedOrders) {
            try {
                orderService.retryCaptureForDeliveredOrder(order);
                captureRetried++;
                log.info("SAFETY_NET_CAPTURE_RETRIED — orderId={}", order.getPublicOrderId());
                auditLogger.logCaptureAttempt(order.getPublicOrderId(), "success", true, null);
            } catch (Exception ex) {
                errors++;
                log.error("SAFETY_NET_ERROR — capture retry failed for orderId={} error={}",
                        order.getPublicOrderId(), ex.getMessage(), ex);
                auditLogger.logCaptureAttempt(order.getPublicOrderId(), "failure", true, ex.getMessage());
            }
        }

        auditLogger.logSafetyNetRun(autoDelivered, captureRetried, errors);
        log.info("SAFETY_NET_COMPLETE — autoDelivered={} captureRetried={} errors={}", autoDelivered, captureRetried, errors);
    }
}
