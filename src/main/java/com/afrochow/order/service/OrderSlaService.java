package com.afrochow.order.service;

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
 * Scheduled job that enforces the vendor acceptance SLA for PENDING orders.
 *
 * When a customer places an order it enters PENDING state — the vendor must
 * accept or reject within the configured window (default 10 minutes).
 * If the window expires without action, this service automatically cancels
 * the order and refunds the customer.
 *
 * Configuration (application.properties / per-environment overrides):
 *   order.sla.accept-window-minutes   — how long a vendor has to accept  (default 10)
 *   order.sla.check-interval-ms       — how often this job runs in ms    (default 60 000 = 1 min)
 */
@Slf4j
@Service
public class OrderSlaService {

    @Value("${order.sla.accept-window-minutes:10}")
    private int acceptWindowMinutes;

    private final OrderRepository orderRepository;
    private final OrderService    orderService;

    public OrderSlaService(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService    = orderService;
    }

    /**
     * Runs every minute (configurable via order.sla.check-interval-ms).
     *
     * Finds all PENDING orders whose {@code orderTime} predates the SLA cutoff
     * and auto-cancels each one via {@link OrderService#autoExpireOrder}.
     *
     * ShedLock ensures only ONE instance acquires the lock per cycle — preventing
     * duplicate Stripe cancel calls when multiple app instances are running.
     * lockAtMostFor = 55 s (safety valve if instance crashes mid-sweep).
     * lockAtLeastFor = 50 s (prevents rapid re-runs if the sweep finishes early).
     */
    @Scheduled(fixedDelayString = "${order.sla.check-interval-ms:60000}")
    @SchedulerLock(
            name          = "OrderSlaService_expireStaleOrders",
            lockAtMostFor = "PT55S",
            lockAtLeastFor= "PT50S"
    )
    public void expireStaleOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(acceptWindowMinutes);

        List<Order> expired = orderRepository.findExpiredPendingOrders(cutoff);

        if (expired.isEmpty()) return;

        log.info("SLA check: found {} PENDING order(s) past the {}‑minute acceptance window — auto-cancelling.",
                expired.size(), acceptWindowMinutes);

        int cancelled = 0;
        int errors    = 0;

        for (Order order : expired) {
            try {
                orderService.autoExpireOrder(order);
                cancelled++;
                log.info("SLA expired — auto-cancelled order {} (placed at {})",
                        order.getPublicOrderId(), order.getOrderTime());
            } catch (Exception ex) {
                errors++;
                log.error("SLA auto-cancel failed for order {} — will retry next cycle.",
                        order.getPublicOrderId(), ex);
            }
        }

        log.info("SLA cycle complete — cancelled: {}, errors: {}", cancelled, errors);
    }
}
