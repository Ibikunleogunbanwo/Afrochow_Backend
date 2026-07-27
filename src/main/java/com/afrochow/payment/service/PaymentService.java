package com.afrochow.payment.service;

import com.afrochow.common.enums.NotificationType;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.common.enums.RelatedEntityType;
import com.afrochow.notification.service.NotificationService;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.payment.dto.PaymentResponseDto;
import com.afrochow.payment.dto.PaymentStatsDto;
import com.afrochow.payment.model.Payment;
import com.afrochow.payment.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferReversalCollectionCreateParams;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing payments.
 * Handles both real Stripe charges (chargeOrder, refundStripeCharge)
 * and internal payment record management.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final OutboxEventService outboxEventService;

    @Value("${stripe.platform.fee-percent:10}")
    private int platformFeePercent;

    @Value("${stripe.connect.required:true}")
    private boolean connectRequired;

    /**
     * Self-reference via Spring proxy — allows calling @Transactional(REQUIRES_NEW)
     * methods on this bean from within the same class. @Lazy prevents a circular
     * dependency at construction time (Spring injects a CGLIB proxy on first use).
     */
    @Autowired
    @Lazy
    private PaymentService self;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            NotificationService notificationService,
            OutboxEventService outboxEventService
    ) {
        this.paymentRepository   = paymentRepository;
        this.orderRepository     = orderRepository;
        this.notificationService = notificationService;
        this.outboxEventService  = outboxEventService;
    }

    // ========== STRIPE METHODS ==========

    /**
     * Outcome of a single authorization attempt (initial charge or a retry).
     * {@code status} is one of AUTHORIZED, PENDING (== 3D Secure action required —
     * reuses the existing PaymentStatus value, no schema change needed), or FAILED.
     * {@code clientSecret} is only populated when status == PENDING — the frontend
     * needs it to run stripe.confirmCardPayment(). {@code failureMessage} is only
     * populated when status == FAILED.
     */
    public record ChargeOutcome(PaymentStatus status, String clientSecret, String failureMessage) {
        public boolean isAuthorized()   { return status == PaymentStatus.AUTHORIZED; }
        public boolean requiresAction() { return status == PaymentStatus.PENDING; }
        public boolean isFailed()       { return status == PaymentStatus.FAILED; }
    }

    /**
     * Authorise (but do NOT capture) the customer's card via Stripe.
     * Uses capture_method=manual so money is only held, not moved.
     * The actual capture happens in captureStripePayment() when the vendor accepts the order.
     * If the vendor rejects or the customer cancels while still PENDING, the hold is released
     * by cancelling the PaymentIntent — no refund needed.
     *
     * On success       : updates Payment record to AUTHORIZED, returns ChargeOutcome.
     * On 3DS required   : updates Payment record to PENDING (with the intent id), returns
     *                     ChargeOutcome carrying the client secret. Does NOT throw — the
     *                     caller (OrderService.createOrder) must NOT roll back the order in
     *                     this case, since the customer still needs to complete the 3D Secure
     *                     challenge to finish paying for an order that legitimately exists.
     * On real failure  : updates Payment record to FAILED and throws RuntimeException —
     *                     callers should let this roll back the surrounding order creation.
     *
     * @param order           saved Order with totalAmount already calculated
     * @param paymentMethodId Stripe payment method token from frontend
     */
    @Transactional
    public ChargeOutcome chargeOrder(Order order, String paymentMethodId) {
        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment record not found for order: " + order.getPublicOrderId()));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not in pending status");
        }

        if (payment.getTransactionId() != null && !payment.getTransactionId().isBlank()) {
            // Defensive guard against creating multiple PaymentIntents for the same order.
            // chargeOrder is expected to be called once per order.
            log.warn("payment.charge.skipped_existing_intent publicOrderId={} paymentId={} transactionId={}",
                    order.getPublicOrderId(),
                    payment.getPaymentId(),
                    payment.getTransactionId());
            return new ChargeOutcome(payment.getStatus(), null, null);
        }

        ChargeOutcome outcome = attemptAuthorization(order, payment, paymentMethodId, "authorize");
        if (outcome.isFailed()) {
            // A genuine decline/error — the order this payment belongs to has no reason
            // to exist without a successful (or pending-3DS) charge, so we throw here and
            // let @Transactional roll back the whole order+payment insert in OrderService.
            throw new RuntimeException("Payment failed: " + outcome.failureMessage());
        }
        return outcome;
    }

    /**
     * Re-checks a PaymentIntent that previously came back "requires_action" (3D Secure),
     * after the frontend has run stripe.confirmCardPayment() against the client secret.
     * Called by the customer-facing confirm endpoint.
     *
     * On success        : same bookkeeping as chargeOrder's happy path — Payment becomes
     *                      AUTHORIZED. Caller is expected to fire the order-placed
     *                      notifications now, since this is the first point the order is
     *                      genuinely paid for.
     * Still needs action: intent is still requires_action (customer closed the 3DS modal
     *                      without finishing it) — returns the same client secret so the
     *                      frontend can re-prompt.
     * Failed            : intent was cancelled or otherwise dead — Payment becomes FAILED.
     */
    @Transactional
    public ChargeOutcome confirmAfter3ds(Order order, Payment payment) {
        if (payment.getStatus() == PaymentStatus.AUTHORIZED || payment.getStatus() == PaymentStatus.COMPLETED) {
            // Already confirmed — likely a duplicate confirm call. No-op, report current state.
            return new ChargeOutcome(payment.getStatus(), null, null);
        }
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getTransactionId() == null) {
            throw new IllegalStateException("No pending 3D Secure authentication to confirm for this order");
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(payment.getTransactionId());

            if ("requires_capture".equals(intent.getStatus())) {
                recordSuccessfulAuthorization(order, payment, intent);
                // This is the first point the order is genuinely paid for — the initial
                // chargeOrder() call deliberately skipped these when 3DS was required, so
                // the vendor is only ever notified of orders that actually have funds held.
                outboxEventService.orderPlaced(order.getPublicOrderId());
                outboxEventService.customerOrderReceived(order.getPublicOrderId());
                return new ChargeOutcome(PaymentStatus.AUTHORIZED, null, null);
            } else if ("requires_action".equals(intent.getStatus())) {
                log.info("payment.still_requires_action publicOrderId={} paymentId={} transactionId={}",
                        order.getPublicOrderId(), payment.getPaymentId(), payment.getTransactionId());
                return new ChargeOutcome(PaymentStatus.PENDING, intent.getClientSecret(), null);
            } else {
                return failAndRecord(order, payment,
                        "3D Secure authentication was not completed (status: " + intent.getStatus() + ")");
            }
        } catch (StripeException e) {
            return failAndRecord(order, payment, cleanStripeMessage(e));
        }
    }

    /**
     * Shared authorization attempt used by both the initial charge (chargeOrder) and a
     * customer-initiated retry with a new card (retryPayment). Always leaves the Payment
     * row in a DB-consistent state matching the returned outcome — callers decide whether
     * a FAILED outcome should roll back a surrounding transaction (chargeOrder does;
     * retryPayment does not, since "the retry also failed" is a normal response to show
     * the customer, not a 500).
     *
     * @param attemptTag distinguishes the Stripe idempotency key between the initial
     *                    charge and any retries, so a retry with a different card is never
     *                    mistaken by Stripe for a duplicate of the original attempt.
     */
    private ChargeOutcome attemptAuthorization(Order order, Payment payment, String paymentMethodId, String attemptTag) {
        String vendorStripeAccountId = order.getVendor().getStripeAccountId();
        boolean useConnect = connectRequired &&
                vendorStripeAccountId != null &&
                !vendorStripeAccountId.isBlank();
        if (connectRequired && !useConnect) {
            return failAndRecord(order, payment, "Vendor does not have a Stripe account configured for payouts");
        }

        try {
            // Stripe works in the smallest currency unit — convert dollars to cents
            long amountInCents = order.getTotalAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("cad")
                    .setPaymentMethod(paymentMethodId)
                    .setConfirm(true)
                    // Manual capture: authorise the card now, capture when vendor accepts
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .setDescription("Afrochow order " + order.getPublicOrderId())
                    .putMetadata("publicOrderId",    order.getPublicOrderId())
                    .putMetadata("vendorPublicId",   order.getVendor().getPublicVendorId())
                    .putMetadata("customerPublicId", order.getCustomer().getUser().getPublicUserId())
                    .setReturnUrl("https://afrochow.ca/order-confirmation/" + order.getPublicOrderId());

            if (useConnect) {
                // transfer_group links this PaymentIntent to the future Transfer we will
                // create at delivery time.  We do NOT set transfer_data.destination here —
                // that would move funds to the vendor immediately at capture, making
                // reverseTransfer the only cancellation path (which fails when the vendor's
                // Stripe balance is already paid out to their bank).
                // Instead: capture goes to the platform account; transferToVendor() sends
                // the vendor's share via a separate Transfer object at DELIVERED time.
                paramsBuilder.setTransferGroup("ORDER_" + order.getPublicOrderId());
            }

            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey("afrochow:order:" + order.getPublicOrderId() + ":" + attemptTag)
                    .build();
            PaymentIntent intent = PaymentIntent.create(paramsBuilder.build(), requestOptions);

            if ("requires_capture".equals(intent.getStatus())) {
                recordSuccessfulAuthorization(order, payment, intent);
                return new ChargeOutcome(PaymentStatus.AUTHORIZED, null, null);

            } else if ("requires_action".equals(intent.getStatus())) {
                // 3D Secure required — client secret is surfaced to the frontend
                payment.setStatus(PaymentStatus.PENDING);
                payment.setTransactionId(intent.getId());
                payment.setNotes("Requires 3D Secure authentication");
                paymentRepository.save(payment);
                log.info("payment.requires_action publicOrderId={} paymentId={} transactionId={}",
                        order.getPublicOrderId(),
                        payment.getPaymentId(),
                        payment.getTransactionId());
                return new ChargeOutcome(PaymentStatus.PENDING, intent.getClientSecret(), null);

            } else {
                return failAndRecord(order, payment, "Stripe payment failed with status: " + intent.getStatus());
            }

        } catch (StripeException e) {
            return failAndRecord(order, payment, cleanStripeMessage(e));
        }
    }

    /**
     * Bookkeeping for a successful authorization (requires_capture): computes and stores
     * the platform fee / vendor payout split, pulls card display details, marks the Payment
     * AUTHORIZED. Shared by the initial charge, a retry, and the post-3DS confirm path —
     * all three end up here whenever Stripe reports the intent is authorized and ready
     * for a later capture.
     */
    private void recordSuccessfulAuthorization(Order order, Payment payment, PaymentIntent intent) {
        String last4 = null;
        String brand = null;
        try {
            com.stripe.model.PaymentMethod stripeMethod =
                    com.stripe.model.PaymentMethod.retrieve(intent.getPaymentMethod());
            if (stripeMethod.getCard() != null) {
                last4 = stripeMethod.getCard().getLast4();
                brand = stripeMethod.getCard().getBrand();
            }
        } catch (StripeException ignored) {
            // Don't fail the order over cosmetic card details
        }

        // Platform fee: e.g. 10% of subtotal (excluding tax) → kept by Afrochow; remainder goes to vendor
        long subtotalInCents = order.getSubtotal()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long feeInCents = BigDecimal.valueOf(subtotalInCents)
                .multiply(BigDecimal.valueOf(platformFeePercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();

        payment.authorizePayment(last4, brand);
        payment.setTransactionId(intent.getId());
        payment.setPlatformFeeAmount(
                BigDecimal.valueOf(feeInCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        payment.setVendorPayout(
                BigDecimal.valueOf(subtotalInCents - feeInCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        paymentRepository.save(payment);
        log.info("payment.authorized publicOrderId={} paymentId={} transactionId={} amount={} fee={} vendorPayout={}",
                order.getPublicOrderId(),
                payment.getPaymentId(),
                payment.getTransactionId(),
                payment.getAmount(),
                payment.getPlatformFeeAmount(),
                payment.getVendorPayout());
    }

    /**
     * Extracts a clean, customer-safe message from a StripeException.
     *
     * StripeException#getMessage() appends internal diagnostics meant for logs, not
     * customers — e.g. "Your card has insufficient funds.; code: card_declined;
     * request-id: req_...". The underlying StripeError, when present, carries just
     * the human-readable reason ("Your card has insufficient funds."). Use this for
     * any message that might end up shown directly to a customer (decline reasons at
     * checkout); use e.getMessage() directly for internal logging where the extra
     * diagnostics are useful.
     */
    private String cleanStripeMessage(StripeException e) {
        if (e.getStripeError() != null && e.getStripeError().getMessage() != null) {
            return e.getStripeError().getMessage();
        }
        return e.getMessage();
    }

    /**
     * Marks the payment FAILED, persists the reason, and fires the customer-facing
     * paymentFailed outbox notification. Centralised here so every failure path (create,
     * retry, confirm) records the same shape of failure consistently.
     *
     * The notification write goes through {@link #self} on a REQUIRES_NEW transaction
     * rather than joining the caller's — this matters specifically for the very first
     * charge attempt at checkout: chargeOrder() throws on a genuine decline, which makes
     * OrderService.createOrder() roll back the *entire* order-creation transaction (the
     * order was never really created). Without REQUIRES_NEW, this outbox write would be
     * rolled back right along with it and the customer would never be told their payment
     * failed outside of the live checkout error toast — which is exactly what was
     * happening before this fix. retryPayment/confirmAfter3ds don't roll back on failure
     * anyway, so REQUIRES_NEW is a no-op there, just consistently applied.
     */
    private ChargeOutcome failAndRecord(Order order, Payment payment, String message) {
        payment.failPayment();
        payment.setNotes(message != null ? ("Stripe error: " + message) : "Payment failed");
        paymentRepository.save(payment);
        log.warn("payment.failed publicOrderId={} paymentId={} message={}",
                order.getPublicOrderId(),
                payment.getPaymentId(),
                message);

        self.recordPaymentFailedNotification(
                order.getCustomer().getUser().getPublicUserId(),
                order.getPublicOrderId(),
                message
        );

        return new ChargeOutcome(PaymentStatus.FAILED, null, message);
    }

    /**
     * Writes the paymentFailed outbox event in its own transaction — see failAndRecord's
     * javadoc for why this needs to survive a surrounding rollback. Must be called via
     * {@link #self} (the Spring proxy), not directly — a same-class call would bypass the
     * proxy and just join whatever transaction is already active, defeating the point.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPaymentFailedNotification(String userPublicId, String publicOrderId, String message) {
        outboxEventService.paymentFailed(userPublicId, publicOrderId, message);
    }

    /**
     * Capture a previously-authorised PaymentIntent.
     * Called when the vendor marks an order delivered, or by the safety net scheduler.
     * This is the moment money actually moves from the customer to Afrochow/vendor.
     *
     * On success : updates Payment record to COMPLETED.
     * On failure : throws RuntimeException.
     *
     * @param order       the order whose payment is to be captured
     * @param finalAmount optional partial capture amount (e.g. item substituted).
     *                    Pass null to capture the full authorized amount.
     */
    @Transactional
    public void captureStripePayment(Order order, BigDecimal finalAmount) {
        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment record not found for order: " + order.getPublicOrderId()));

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Cannot capture — payment is not in AUTHORIZED state (current: " + payment.getStatus() + ")");
        }

        if (payment.getTransactionId() == null) {
            throw new IllegalStateException(
                    "Cannot capture — no Stripe PaymentIntent ID on record");
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(payment.getTransactionId());
            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey("afrochow:order:" + order.getPublicOrderId() + ":capture")
                    .build();

            PaymentIntentCaptureParams.Builder captureParamsBuilder = PaymentIntentCaptureParams.builder();
            if (finalAmount != null) {
                long finalAmountCents = finalAmount
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, java.math.RoundingMode.HALF_UP)
                        .longValueExact();
                captureParamsBuilder.setAmountToCapture(finalAmountCents);
            }

            PaymentIntent captured = intent.capture(captureParamsBuilder.build(), requestOptions);

            if ("succeeded".equals(captured.getStatus())) {
                // If a partial capture was requested, update the payment record to reflect
                // the actual amount charged rather than the original authorization amount.
                // Also recalculate the platform fee and vendor payout proportionally so
                // reconciliation reports stay accurate.
                if (finalAmount != null) {
                    BigDecimal capturedAmount = finalAmount.setScale(2, RoundingMode.HALF_UP);
                    long capturedCents = capturedAmount
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(0, RoundingMode.HALF_UP)
                            .longValue();
                    // Fee is on subtotal only — back out tax proportionally from captured amount
                    BigDecimal taxRate = order.getTaxRate() != null ? order.getTaxRate() : BigDecimal.ZERO;
                    BigDecimal capturedSubtotal = capturedAmount
                            .divide(BigDecimal.ONE.add(taxRate), 10, RoundingMode.HALF_UP);
                    long capturedSubtotalCents = capturedSubtotal
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(0, RoundingMode.HALF_UP)
                            .longValue();
                    long feeCents = BigDecimal.valueOf(capturedSubtotalCents)
                            .multiply(BigDecimal.valueOf(platformFeePercent))
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                            .longValue();
                    payment.setAmount(capturedAmount);
                    payment.setPlatformFeeAmount(
                            BigDecimal.valueOf(feeCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                    payment.setVendorPayout(
                            BigDecimal.valueOf(capturedSubtotalCents - feeCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                    log.info("payment.partial_capture publicOrderId={} originalAmount={} capturedAmount={} fee={} vendorPayout={}",
                            order.getPublicOrderId(),
                            order.getTotalAmount(),
                            capturedAmount,
                            payment.getPlatformFeeAmount(),
                            payment.getVendorPayout());
                }

                BigDecimal actualCapturedAmount = payment.getAmount();
                payment.completePayment(payment.getCardLast4(), payment.getCardBrand());
                paymentRepository.save(payment);
                log.info("payment.captured publicOrderId={} paymentId={} transactionId={} amount={}",
                        order.getPublicOrderId(),
                        payment.getPaymentId(),
                        payment.getTransactionId(),
                        actualCapturedAmount);

                outboxEventService.paymentCaptured(
                        order.getCustomer().getUser().getPublicUserId(),
                        captured.getId(),
                        order.getPublicOrderId(),
                        actualCapturedAmount
                );
            } else {
                throw new RuntimeException(
                        "Stripe capture returned unexpected status: " + captured.getStatus());
            }

        } catch (StripeException e) {
            log.warn("payment.capture.failed publicOrderId={} paymentId={} transactionId={} message={}",
                    order.getPublicOrderId(),
                    payment.getPaymentId(),
                    payment.getTransactionId(),
                    e.getMessage());
            throw new RuntimeException("Payment capture failed: " + e.getMessage());
        }
    }

    /**
     * Pay out the vendor's share to their connected Stripe account after the order
     * is marked DELIVERED.
     *
     * Under the transfer_group model the platform account holds all captured funds.
     * This method creates a Stripe Transfer for (totalAmount − platformFee) linked
     * to the original charge via source_transaction.  That linkage means the Transfer
     * draws from the specific charge's funds rather than the platform account's
     * general balance, which is both cleaner and better for reconciliation.
     *
     * Idempotent — if a transfer ID is already stored on the Payment record the
     * method returns immediately (safe to retry from the safety-net scheduler).
     *
     * @param order the DELIVERED order whose vendor should be paid
     */
    @Transactional
    public void transferToVendor(Order order) {
        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment record not found for order: " + order.getPublicOrderId()));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot transfer — payment is not COMPLETED (current: " + payment.getStatus() + ")");
        }

        // Idempotency guard — transfer already created
        if (payment.getStripeTransferId() != null && !payment.getStripeTransferId().isBlank()) {
            log.warn("payment.transfer.already_done publicOrderId={} transferId={}",
                    order.getPublicOrderId(), payment.getStripeTransferId());
            return;
        }

        String vendorStripeAccountId = order.getVendor().getStripeAccountId();
        if (vendorStripeAccountId == null || vendorStripeAccountId.isBlank()) {
            throw new IllegalStateException(
                    "Vendor does not have a Stripe account configured for payouts");
        }

        try {
            // Retrieve the PaymentIntent to get the underlying Charge ID.
            // source_transaction on a Transfer must be a Charge (ch_...) not a PaymentIntent (pi_...).
            PaymentIntent intent = PaymentIntent.retrieve(payment.getTransactionId());
            String chargeId = intent.getLatestCharge();
            if (chargeId == null || chargeId.isBlank()) {
                throw new IllegalStateException(
                        "No charge found on PaymentIntent " + payment.getTransactionId());
            }

            // Vendor payout = total - platform fee, pre-calculated and stored at authorization time
            long vendorPayoutCents = payment.getVendorPayout()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            TransferCreateParams transferParams = TransferCreateParams.builder()
                    .setAmount(vendorPayoutCents)
                    .setCurrency("cad")
                    .setDestination(vendorStripeAccountId)
                    // Links this Transfer to the charge — draws from those specific funds
                    .setSourceTransaction(chargeId)
                    // Same group as the PaymentIntent for unified Stripe reporting
                    .setTransferGroup("ORDER_" + order.getPublicOrderId())
                    .putMetadata("publicOrderId",  order.getPublicOrderId())
                    .putMetadata("vendorPublicId", order.getVendor().getPublicVendorId())
                    .build();

            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey("afrochow:order:" + order.getPublicOrderId() + ":transfer")
                    .build();

            Transfer transfer = Transfer.create(transferParams, requestOptions);

            payment.setStripeTransferId(transfer.getId());
            paymentRepository.save(payment);

            log.info("payment.transfer.created publicOrderId={} transferId={} vendorPayout={}",
                    order.getPublicOrderId(), transfer.getId(), payment.getVendorPayout());

        } catch (StripeException e) {
            log.error("payment.transfer.failed publicOrderId={} message={}",
                    order.getPublicOrderId(), e.getMessage(), e);
            throw new RuntimeException("Vendor transfer failed: " + e.getMessage());
        }
    }

    @Transactional
    public void transferToVendor(String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + publicOrderId));
        transferToVendor(order);
    }

    /**
     * Refund a completed Stripe charge, or cancel the hold if the payment was
     * only authorised and not yet captured.
     * Called by OrderService.cancelCustomerOrder() / rejectOrder() / adminCancelOrder().
     * Safe to call on unpaid/failed orders — returns silently.
     *
     * @param order the order to refund or cancel
     */
    @Transactional
    public void refundStripeCharge(Order order) {
        // Use a pessimistic write lock on the Payment row so two concurrent refund
        // paths (e.g. SLA scheduler + customer cancel) cannot both reach Stripe
        // with the same PaymentIntent and trigger a double-cancel / double-refund.
        Payment payment = paymentRepository.findByOrderWithLock(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment record not found for order: " + order.getPublicOrderId()));

        if (payment.getTransactionId() == null) {
            // No Stripe record — nothing to do
            return;
        }

        try {
            if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
                // ── Payment was never captured — cancel the hold instead of refunding ──
                // No money was moved, so no refund is needed. Cancelling the intent
                // releases the authorisation hold on the customer's card immediately.
                PaymentIntent intent = PaymentIntent.retrieve(payment.getTransactionId());
                RequestOptions requestOptions = RequestOptions.builder()
                        .setIdempotencyKey("afrochow:order:" + order.getPublicOrderId() + ":cancel_auth")
                        .build();
                intent.cancel(requestOptions);
                payment.cancelAuthorization();
                paymentRepository.save(payment);
                log.info("payment.authorization.cancelled publicOrderId={} paymentId={} transactionId={}",
                        order.getPublicOrderId(),
                        payment.getPaymentId(),
                        payment.getTransactionId());

            } else if (payment.getStatus() == PaymentStatus.COMPLETED) {
                // ── Payment was captured — issue a real Stripe refund ──
                //
                // Under the transfer_group model funds sit in the *platform* account
                // until transferToVendor() runs at delivery.
                //
                // Case A — no transfer yet (order cancelled before delivery):
                //   Refund straight from platform funds.  No reverseTransfer needed.
                //
                // Case B — transfer already done (post-delivery admin refund):
                //   First reverse the vendor transfer so funds return to the platform,
                //   then refund the customer from the platform.
                boolean transferDone = payment.getStripeTransferId() != null
                        && !payment.getStripeTransferId().isBlank();

                if (transferDone) {
                    // Reverse the vendor transfer — brings funds back to platform account
                    Transfer transfer = Transfer.retrieve(payment.getStripeTransferId());
                    RequestOptions reverseOptions = RequestOptions.builder()
                            .setIdempotencyKey("afrochow:order:" + order.getPublicOrderId() + ":transfer_reversal")
                            .build();
                    transfer.getReversals().create(
                            TransferReversalCollectionCreateParams.builder().build(),
                            reverseOptions);
                    log.info("payment.transfer.reversed publicOrderId={} transferId={}",
                            order.getPublicOrderId(), payment.getStripeTransferId());
                }

                // Refund the customer — always from platform account (no reverseTransfer flag)
                RequestOptions refundOptions = RequestOptions.builder()
                        .setIdempotencyKey("afrochow:order:" + order.getPublicOrderId() + ":refund")
                        .build();
                com.stripe.model.Refund.create(
                        com.stripe.param.RefundCreateParams.builder()
                                .setPaymentIntent(payment.getTransactionId())
                                .build(),
                        refundOptions);

                payment.refundPayment();
                paymentRepository.save(payment);
                log.info("payment.refunded publicOrderId={} paymentId={} transactionId={} amount={}",
                        order.getPublicOrderId(),
                        payment.getPaymentId(),
                        payment.getTransactionId(),
                        payment.getAmount());

            }
            // All other statuses (PENDING, FAILED, CANCELLED, REFUNDED) — nothing to do

        } catch (StripeException e) {
            log.warn("payment.refund_or_cancel.failed publicOrderId={} paymentId={} transactionId={} message={}",
                    order.getPublicOrderId(),
                    payment.getPaymentId(),
                    payment.getTransactionId(),
                    e.getMessage());
            throw new RuntimeException("Stripe refund/cancel failed: " + e.getMessage());
        }
    }

    /**
     * Marks the Payment record as CANCELLED in the DB without calling Stripe.
     * Joins the caller's transaction (REQUIRED propagation).
     * Called from {@link com.afrochow.order.service.OrderService#commitOrderExpiry}
     * during SLA auto-expiry, where the Stripe API call happens AFTER this DB commit.
     *
     * <p>Handles both AUTHORIZED (normal case — a capturable hold that never got
     * captured within the SLA window) and PENDING (a Stripe PaymentIntent stuck
     * in requires_action — e.g. the customer abandoned a 3D Secure challenge —
     * that never resolved to AUTHORIZED before the order expired). Previously
     * this only handled AUTHORIZED, so an abandoned-3DS payment would silently
     * stay PENDING forever even after its order was auto-cancelled, desyncing
     * Order.status (CANCELLED) from Payment.status (still PENDING) with no
     * error raised — invisible to admin dashboards and reconciliation alike.
     */
    @Transactional
    public void markPaymentCancelled(Order order) {
        Payment payment = paymentRepository.findByOrderWithLock(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment record not found for order: " + order.getPublicOrderId()));
        PaymentStatus previousStatus = payment.getStatus();
        if (previousStatus != PaymentStatus.AUTHORIZED && previousStatus != PaymentStatus.PENDING) {
            // Already cancelled/refunded/completed — nothing to do
            return;
        }
        payment.cancelAuthorization();
        paymentRepository.save(payment);
        log.info("payment.record.marked_cancelled publicOrderId={} paymentId={} previousStatus={}",
                order.getPublicOrderId(), payment.getPaymentId(), previousStatus);
    }

    /**
     * Cancels the Stripe PaymentIntent authorization for an order.
     * Intentionally NOT @Transactional — must be called OUTSIDE any active JPA transaction
     * so that a Stripe failure cannot roll back already-committed DB state.
     * Idempotent: uses a stable idempotency key so repeated calls are safe.
     *
     * If Stripe returns an error (e.g. the intent is already cancelled), logs a warning
     * and swallows the exception — the authorization hold expires on its own after 7 days.
     */
    public void cancelStripeAuthorization(Order order) {
        Payment payment = paymentRepository.findByOrder(order).orElse(null);
        if (payment == null || payment.getTransactionId() == null) return;

        try {
            PaymentIntent intent = PaymentIntent.retrieve(payment.getTransactionId());
            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey("afrochow:order:" + order.getPublicOrderId() + ":cancel_auth")
                    .build();
            intent.cancel(requestOptions);
            log.info("payment.authorization.cancelled publicOrderId={} paymentId={} transactionId={}",
                    order.getPublicOrderId(),
                    payment.getPaymentId(),
                    payment.getTransactionId());
        } catch (StripeException e) {
            log.error("payment.stripe_cancel.failed publicOrderId={} transactionId={} — " +
                      "authorization will expire on Stripe after 7 days. Error: {}",
                    order.getPublicOrderId(), payment.getTransactionId(), e.getMessage());
            // Do NOT rethrow — order is already CANCELLED in DB; Stripe hold expires naturally.
        }
    }

    // ========== INTERNAL HELPERS ==========

    /**
     * Returns the raw Payment entity for a given Order.
     * Intended for internal service-to-service use (e.g. OrderService checking
     * payment status before deciding whether to capture or transfer).
     * Does not perform any authorisation check.
     */
    public Payment getPaymentByOrder(Order order) {
        return paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment record not found for order: " + order.getPublicOrderId()));
    }

    // ========== CUSTOMER METHODS ==========

    /**
     * Get payment for an order (customer)
     */
    public PaymentResponseDto getPaymentByOrderId(Long customerUserId, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException(
                    "You can only view payments for your own orders");
        }

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        return toResponseDto(payment);
    }

    /**
     * Confirm a payment after the customer completed a 3D Secure challenge on the
     * frontend (stripe.confirmCardPayment against the client secret returned from
     * order creation or a prior retry). Re-checks the PaymentIntent with Stripe and
     * finalizes the Payment record accordingly.
     */
    @Transactional
    public PaymentResponseDto confirmPayment(Long customerUserId, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException(
                    "You can only confirm payments for your own orders");
        }

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        ChargeOutcome outcome = confirmAfter3ds(order, payment);
        return toResponseDtoWithOutcome(paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for this order")), outcome);
    }

    /**
     * Retry a failed payment with a new card. Unlike chargeOrder(), a FAILED outcome
     * here is returned to the caller rather than thrown — the order already exists and
     * "that card didn't work either" is a normal response the customer should see and
     * act on, not a 500.
     */
    @Transactional
    public PaymentResponseDto retryPayment(Long customerUserId, String publicOrderId, String paymentMethodId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException(
                    "You can only retry payments for your own orders");
        }
        if (paymentMethodId == null || paymentMethodId.isBlank()) {
            throw new IllegalArgumentException("A new payment method is required to retry payment");
        }

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException("Can only retry failed payments");
        }

        // Clear the dead intent from the previous failed attempt before trying again.
        payment.setTransactionId(null);
        payment.setNotes(null);
        paymentRepository.save(payment);

        ChargeOutcome outcome = attemptAuthorization(order, payment, paymentMethodId,
                "retry-" + System.currentTimeMillis());

        if (outcome.isAuthorized()) {
            // A FAILED payment on an existing order means the vendor was never notified
            // of it (chargeOrder() only notifies on immediate success; confirmAfter3ds()
            // notifies on 3DS success — a payment only reaches FAILED via one of those
            // two paths declining). This retry succeeding is therefore the first genuine
            // confirmation for this order, so fire the same notifications here too.
            outboxEventService.orderPlaced(order.getPublicOrderId());
            outboxEventService.customerOrderReceived(order.getPublicOrderId());
        }

        return toResponseDtoWithOutcome(paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for this order")), outcome);
    }

    // ========== ADMIN METHODS ==========

    /**
     * Get all payments (admin)
     */
    public List<PaymentResponseDto> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get payment by transaction ID (admin)
     */
    public PaymentResponseDto getPaymentByTransactionId(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found"));
        return toResponseDto(payment);
    }

    /**
     * Get payment by order public ID (admin)
     */
    public PaymentResponseDto getPaymentByOrderIdAdmin(String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        return toResponseDto(payment);
    }

    /**
     * Get payments by status (admin)
     */
    public List<PaymentResponseDto> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get failed payments (admin)
     */
    public List<PaymentResponseDto> getFailedPayments() {
        return paymentRepository.findFailedPayments().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Refund payment via admin — calls real Stripe refund.
     *
     * <p>{@link #refundStripeCharge} is a shared helper also used by order-cancellation
     * flows, where it's deliberately a silent no-op for PENDING/FAILED/CANCELLED/REFUNDED
     * payments (nothing to reverse). That's correct there, but wrong for this admin
     * action: an admin clicking "Refund" expects either money to actually move or a
     * clear error — not a false "refunded successfully" response and a "your payment
     * has been refunded" email sent to a customer whose payment was never charged.
     * So we check the payment is in a refundable state up front and fail loudly if not.
     */
    @Transactional
    public PaymentResponseDto refundPayment(String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        Payment paymentBefore = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        if (paymentBefore.getStatus() != PaymentStatus.COMPLETED
                && paymentBefore.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Cannot refund — payment is " + paymentBefore.getStatus()
                            + ", not COMPLETED or AUTHORIZED. There is no charge to reverse.");
        }

        refundStripeCharge(order);

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for this order"));

        notificationService.createNotification(
                order.getCustomer().getUser().getPublicUserId(),
                "Payment Refunded",
                String.format(
                        "Your payment of $%.2f for order #%s has been refunded. " +
                                "The refund will appear in your account within 5-10 business days.",
                        payment.getAmount(), order.getPublicOrderId()),
                NotificationType.PAYMENT_SUCCESS,
                RelatedEntityType.PAYMENT,
                payment.getTransactionId()
        );

        return toResponseDto(payment);
    }

    // ========== STATISTICS ==========

    public Long countPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.countByStatus(status);
    }

    /**
     * Powers the admin Payment Management dashboard's stat cards in one grouped
     * query instead of the frontend fetching every payment and bucketing them
     * client-side (the previous approach, which is how a CANCELLED payment could
     * inflate Total without appearing in any card).
     */
    public PaymentStatsDto getPaymentStats() {
        Map<PaymentStatus, Long> byStatus = new EnumMap<>(PaymentStatus.class);
        for (Object[] row : paymentRepository.countGroupedByStatus()) {
            byStatus.put((PaymentStatus) row[0], (Long) row[1]);
        }
        long pending    = byStatus.getOrDefault(PaymentStatus.PENDING, 0L);
        long authorized = byStatus.getOrDefault(PaymentStatus.AUTHORIZED, 0L);
        long completed  = byStatus.getOrDefault(PaymentStatus.COMPLETED, 0L);
        long failed     = byStatus.getOrDefault(PaymentStatus.FAILED, 0L);
        long refunded   = byStatus.getOrDefault(PaymentStatus.REFUNDED, 0L);
        long cancelled  = byStatus.getOrDefault(PaymentStatus.CANCELLED, 0L);
        long total      = pending + authorized + completed + failed + refunded + cancelled;

        return PaymentStatsDto.builder()
                .total(total)
                .pending(pending)
                .authorized(authorized)
                .completed(completed)
                .failed(failed)
                .refunded(refunded)
                .cancelled(cancelled)
                .build();
    }

    // ========== MAPPING ==========

    private PaymentResponseDto toResponseDto(Payment payment) {
        return PaymentResponseDto.builder()
                .publicOrderId(payment.getOrder() != null
                        ? payment.getOrder().getPublicOrderId() : null)
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .maskedCardNumber(payment.getMaskedCardNumber())
                .cardBrand(payment.getCardBrand())
                .notes(payment.getNotes())
                .isSuccessful(payment.isSuccessful())
                .isPending(payment.isPending())
                .isFailed(payment.isFailed())
                .isRefunded(payment.isRefunded())
                .paymentTime(payment.getPaymentTime())
                .completedAt(payment.getCompletedAt())
                .failedAt(payment.getFailedAt())
                .refundedAt(payment.getRefundedAt())
                .build();
    }

    /**
     * Same as toResponseDto(), plus the requiresAction/stripeClientSecret pair carried
     * on a fresh ChargeOutcome — used by retryPayment() and confirmPayment(), the only
     * two customer-facing calls that can produce a live "still needs 3DS" signal.
     */
    private PaymentResponseDto toResponseDtoWithOutcome(Payment payment, ChargeOutcome outcome) {
        PaymentResponseDto dto = toResponseDto(payment);
        dto.setRequiresAction(outcome.requiresAction());
        dto.setStripeClientSecret(outcome.requiresAction() ? outcome.clientSecret() : null);
        return dto;
    }
}
