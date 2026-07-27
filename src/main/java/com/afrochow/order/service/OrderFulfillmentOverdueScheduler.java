package com.afrochow.order.service;

import com.afrochow.common.audit.OrderAuditLogger;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fulfillment Overdue Scheduler — catches CONFIRMED/PREPARING orders the vendor
 * accepted but never moved forward on.
 *
 * This closes a gap the other two safety nets don't cover:
 *   - {@link OrderSlaService} only protects PENDING orders (vendor hasn't responded yet —
 *     nothing has been charged).
 *   - {@link FulfillmentSafetyNetScheduler} only protects OUT_FOR_DELIVERY/READY_FOR_PICKUP
 *     orders (food is already made/out — this just closes the record and pays the vendor).
 *
 * In between those two — CONFIRMED and PREPARING — the vendor's payment capture has
 * already happened (see OrderService#acceptOrder), so if the vendor accepts an order
 * and then goes silent, the customer has been charged with no automatic resolution.
 * The only prior escape hatches were manual: the vendor calling "unable to fulfil", or
 * an admin finding the order and force-cancelling it.
 *
 * Two-pass design, same pattern as FulfillmentSafetyNetScheduler:
 *
 * Pass 1 — Flag:
 *   Orders whose fulfillmentDeadline (set at accept time — see
 *   OrderService#computeFulfillmentDeadline) plus a short grace period has passed,
 *   and haven't been flagged yet. Notifies the vendor + admins. Does NOT cancel
 *   anything — the vendor may just be running a little behind.
 *
 * Pass 2 — Auto-cancel:
 *   Orders already flagged overdue that are STILL unresolved (still CONFIRMED/PREPARING)
 *   a further grace period after being flagged. These are auto-cancelled and refunded —
 *   same as a vendor calling "unable to fulfil", except the system is the actor.
 *
 * Distributed locking: ShedLock ensures only one instance runs per cycle.
 */
@Slf4j
@Service
public class OrderFulfillmentOverdueScheduler {

    /** Buffer added on top of fulfillmentDeadline before an order is first flagged. */
    @Value("${order.fulfillment.flag-grace-minutes:15}")
    private int flagGraceMinutes;

    /** How long an order can sit flagged-but-unresolved before being auto-cancelled and refunded. */
    @Value("${order.fulfillment.auto-cancel-grace-minutes:120}")
    private int autoCancelGraceMinutes;

    private final OrderRepository  orderRepository;
    private final OrderService     orderService;
    private final OrderAuditLogger auditLogger;

    public OrderFulfillmentOverdueScheduler(
            OrderRepository  orderRepository,
            OrderService     orderService,
            OrderAuditLogger auditLogger
    ) {
        this.orderRepository = orderRepository;
        this.orderService    = orderService;
        this.auditLogger     = auditLogger;
    }

    /**
     * Runs every 5 minutes by default. ShedLock limits execution to one instance at a time.
     * lockAtMostFor = 5 min (safety valve if the instance crashes mid-run).
     * lockAtLeastFor = 1 min (prevents rapid re-runs across instances).
     */
    @Scheduled(fixedDelayString = "${order.fulfillment.overdue-check-interval-ms:300000}")
    @SchedulerLock(
            name           = "OrderFulfillmentOverdueScheduler",
            lockAtMostFor  = "PT5M",
            lockAtLeastFor = "PT1M"
    )
    public void runOverdueCheck() {
        log.info("FULFILLMENT_OVERDUE_START — beginning overdue check");

        int flagged  = 0;
        int cancelled = 0;
        int errors    = 0;

        // ── Pass 1: flag newly-overdue orders ──────────────────────────────────
        LocalDateTime flagCutoff = LocalDateTime.now().minusMinutes(flagGraceMinutes);
        List<Order> newlyOverdue = orderRepository.findNewlyOverdueOrders(flagCutoff);
        log.info("FULFILLMENT_OVERDUE_PASS1 — found {} newly-overdue order(s) (deadline + {}m grace)",
                newlyOverdue.size(), flagGraceMinutes);

        for (Order order : newlyOverdue) {
            try {
                orderService.flagOrderOverdue(order);
                flagged++;
                log.info("FULFILLMENT_OVERDUE_FLAGGED — orderId={} status={} fulfillmentDeadline={}",
                        order.getPublicOrderId(), order.getStatus(), order.getFulfillmentDeadline());
                auditLogger.logOrderTransition(
                        order.getPublicOrderId(),
                        order.getStatus().name(),
                        order.getStatus().name(),
                        "system:overdue-scheduler",
                        "Flagged overdue — past fulfillmentDeadline plus grace period",
                        "FLAGGED",
                        null
                );
            } catch (Exception ex) {
                errors++;
                log.error("FULFILLMENT_OVERDUE_ERROR — flag failed for orderId={}",
                        order.getPublicOrderId(), ex);
            }
        }

        // ── Pass 2: auto-cancel unresolved flagged orders ──────────────────────
        LocalDateTime cancelCutoff = LocalDateTime.now().minusMinutes(autoCancelGraceMinutes);
        List<Order> unresolved = orderRepository.findUnresolvedFlaggedOrders(cancelCutoff);
        log.info("FULFILLMENT_OVERDUE_PASS2 — found {} unresolved flagged order(s) (flagged + {}m grace)",
                unresolved.size(), autoCancelGraceMinutes);

        for (Order order : unresolved) {
            try {
                orderService.autoCancelOverdueOrder(order);
                cancelled++;
                log.info("FULFILLMENT_OVERDUE_AUTO_CANCELLED — orderId={} flaggedAt={}",
                        order.getPublicOrderId(), order.getOverdueFlaggedAt());
                auditLogger.logOrderTransition(
                        order.getPublicOrderId(),
                        order.getStatus().name(),
                        "CANCELLED",
                        "system:overdue-scheduler",
                        "Auto-cancelled and refunded — unresolved past flag grace period",
                        "COMPLETED",
                        null
                );
            } catch (Exception ex) {
                errors++;
                log.error("FULFILLMENT_OVERDUE_ERROR — auto-cancel failed for orderId={}",
                        order.getPublicOrderId(), ex);
            }
        }

        log.info("FULFILLMENT_OVERDUE_COMPLETE — flagged={} cancelled={} errors={}", flagged, cancelled, errors);
    }
}
