package com.afrochow.payment.service;

import com.afrochow.notification.service.NotificationService;
import com.afrochow.payment.model.Payment;
import com.afrochow.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class StrandedPayoutJob {

    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.payments.stranded-payout-monitor-interval-ms:1800000}")
    @SchedulerLock(name = "strandedPayoutMonitor", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void alertOnStrandedPayouts() {
        var stranded = paymentRepository.findCompletedWithoutTransfer();
        if (stranded.isEmpty()) {
            return;
        }

        log.error("payment.stranded_payout.detected count={}", stranded.size());
        for (Payment payment : stranded) {
            String publicOrderId = payment.getOrder() != null ? payment.getOrder().getPublicOrderId() : null;
            BigDecimal vendorPayout = payment.getVendorPayout() != null ? payment.getVendorPayout() : BigDecimal.ZERO;
            log.error("payment.stranded_payout publicOrderId={} paymentId={} transactionId={} vendorPayout={}",
                    publicOrderId, payment.getPaymentId(), payment.getTransactionId(), vendorPayout);
            if (publicOrderId != null) {
                notificationService.notifyAdminsStrandedPayout(publicOrderId, vendorPayout);
            }
        }
    }
}
