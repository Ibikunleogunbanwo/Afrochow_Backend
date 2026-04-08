package com.afrochow.common.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Centralised audit logger for all order and payment events.
 *
 * Combines two observability layers:
 *   1. Structured MDC logging  — every event writes a log line with fields
 *      that can be queried in Datadog, CloudWatch, or any log aggregator.
 *   2. Micrometer metrics      — numerical counters exposed via Spring Actuator
 *      and scrapable by Prometheus or compatible monitoring tools.
 *
 * Metrics published:
 *   afrochow.order.status.transitions   — tags: from, to
 *   afrochow.payment.charge.attempts    — tags: result (success/failure/3ds_required)
 *   afrochow.payment.capture.attempts   — tags: result, retry (true/false)
 *   afrochow.payment.refund.attempts    — tags: type (real_refund/hold_release), result
 */
@Slf4j
@Component
public class OrderAuditLogger {

    private final MeterRegistry meterRegistry;

    public OrderAuditLogger(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // ── Order Events ──────────────────────────────────────────────────────────

    /**
     * Log and count every order status transition.
     *
     * @param publicOrderId   public-facing order reference (e.g. AFC-X7MK2P4N)
     * @param fromStatus      previous status
     * @param toStatus        new status
     * @param actor           who triggered the change (username, "customer", "admin", "system:safety-net")
     * @param reason          human-readable explanation (may be null)
     * @param paymentStatus   current payment state
     * @param stripeIntentId  Stripe PaymentIntent ID for cross-reference (may be null)
     */
    public void logOrderTransition(
            String publicOrderId,
            String fromStatus,
            String toStatus,
            String actor,
            String reason,
            String paymentStatus,
            String stripeIntentId
    ) {
        try {
            MDC.put("publicOrderId",          publicOrderId);
            MDC.put("fromStatus",             fromStatus);
            MDC.put("toStatus",               toStatus);
            MDC.put("actor",                  actor);
            MDC.put("reason",                 reason != null ? reason : "");
            MDC.put("paymentStatus",          paymentStatus != null ? paymentStatus : "");
            MDC.put("stripePaymentIntentId",  stripeIntentId != null ? stripeIntentId : "");

            log.info("ORDER_TRANSITION orderId={} {}→{} actor={} paymentStatus={} stripeIntent={} reason={}",
                    publicOrderId, fromStatus, toStatus, actor, paymentStatus, stripeIntentId, reason);
        } finally {
            MDC.remove("publicOrderId");
            MDC.remove("fromStatus");
            MDC.remove("toStatus");
            MDC.remove("actor");
            MDC.remove("reason");
            MDC.remove("paymentStatus");
            MDC.remove("stripePaymentIntentId");
        }

        Counter.builder("afrochow.order.status.transitions")
                .tag("from", fromStatus != null ? fromStatus : "unknown")
                .tag("to",   toStatus   != null ? toStatus   : "unknown")
                .description("Number of order status transitions")
                .register(meterRegistry)
                .increment();
    }

    // ── Payment Events ────────────────────────────────────────────────────────

    /**
     * Log and count a Stripe authorisation (charge) attempt.
     *
     * @param publicOrderId public-facing order reference
     * @param result        "success", "failure", or "3ds_required"
     * @param errorMessage  error detail when result is "failure" (may be null)
     */
    public void logChargeAttempt(String publicOrderId, String result, String errorMessage) {
        try {
            MDC.put("publicOrderId", publicOrderId);
            MDC.put("chargeResult",  result);
            if (errorMessage != null) MDC.put("chargeError", errorMessage);

            if ("success".equals(result)) {
                log.info("PAYMENT_CHARGE orderId={} result={}", publicOrderId, result);
            } else {
                log.warn("PAYMENT_CHARGE orderId={} result={} error={}", publicOrderId, result, errorMessage);
            }
        } finally {
            MDC.remove("publicOrderId");
            MDC.remove("chargeResult");
            MDC.remove("chargeError");
        }

        Counter.builder("afrochow.payment.charge.attempts")
                .tag("result", result)
                .description("Number of Stripe authorisation attempts")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Log and count a Stripe capture attempt.
     *
     * @param publicOrderId public-facing order reference
     * @param result        "success" or "failure"
     * @param isRetry       true if this is a retry by the safety net scheduler
     * @param errorMessage  error detail when result is "failure" (may be null)
     */
    public void logCaptureAttempt(String publicOrderId, String result, boolean isRetry, String errorMessage) {
        try {
            MDC.put("publicOrderId", publicOrderId);
            MDC.put("captureResult", result);
            MDC.put("captureRetry",  String.valueOf(isRetry));
            if (errorMessage != null) MDC.put("captureError", errorMessage);

            if ("success".equals(result)) {
                log.info("PAYMENT_CAPTURE orderId={} result={} retry={}", publicOrderId, result, isRetry);
            } else {
                log.warn("PAYMENT_CAPTURE orderId={} result={} retry={} error={}", publicOrderId, result, isRetry, errorMessage);
            }
        } finally {
            MDC.remove("publicOrderId");
            MDC.remove("captureResult");
            MDC.remove("captureRetry");
            MDC.remove("captureError");
        }

        Counter.builder("afrochow.payment.capture.attempts")
                .tag("result", result)
                .tag("retry",  String.valueOf(isRetry))
                .description("Number of Stripe capture attempts")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Log and count a Stripe refund or hold-release attempt.
     *
     * @param publicOrderId public-facing order reference
     * @param type          "real_refund" (captured payment) or "hold_release" (authorized only)
     * @param result        "success" or "failure"
     * @param amount        amount refunded (may be null)
     * @param errorMessage  error detail when result is "failure" (may be null)
     */
    public void logRefundAttempt(String publicOrderId, String type, String result,
                                  BigDecimal amount, String errorMessage) {
        try {
            MDC.put("publicOrderId", publicOrderId);
            MDC.put("refundType",    type);
            MDC.put("refundResult",  result);
            if (amount != null)       MDC.put("refundAmount", amount.toPlainString());
            if (errorMessage != null) MDC.put("refundError",  errorMessage);

            if ("success".equals(result)) {
                log.info("PAYMENT_REFUND orderId={} type={} result={} amount={}", publicOrderId, type, result, amount);
            } else {
                log.warn("PAYMENT_REFUND orderId={} type={} result={} error={}", publicOrderId, type, result, errorMessage);
            }
        } finally {
            MDC.remove("publicOrderId");
            MDC.remove("refundType");
            MDC.remove("refundResult");
            MDC.remove("refundAmount");
            MDC.remove("refundError");
        }

        Counter.builder("afrochow.payment.refund.attempts")
                .tag("type",   type)
                .tag("result", result)
                .description("Number of Stripe refund or hold-release attempts")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Log a safety net scheduler run summary.
     *
     * @param autoDelivered   number of orders auto-delivered this run
     * @param captureRetried  number of capture retries this run
     * @param errors          number of errors this run
     */
    public void logSafetyNetRun(int autoDelivered, int captureRetried, int errors) {
        log.info("SAFETY_NET_RUN autoDelivered={} captureRetried={} errors={}", autoDelivered, captureRetried, errors);

        Counter.builder("afrochow.scheduler.safety_net.auto_delivered")
                .description("Orders auto-delivered by the safety net scheduler")
                .register(meterRegistry)
                .increment(autoDelivered);

        Counter.builder("afrochow.scheduler.safety_net.capture_retried")
                .description("Payment captures retried by the safety net scheduler")
                .register(meterRegistry)
                .increment(captureRetried);

        if (errors > 0) {
            Counter.builder("afrochow.scheduler.safety_net.errors")
                    .description("Errors in the safety net scheduler")
                    .register(meterRegistry)
                    .increment(errors);
        }
    }
}
