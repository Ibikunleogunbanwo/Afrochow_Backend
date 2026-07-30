package com.afrochow.payment.service;

import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.notification.service.NotificationService;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.payment.model.Payment;
import com.afrochow.payment.repository.PaymentRepository;
import com.afrochow.user.model.User;
import com.afrochow.vendor.model.VendorProfile;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Refund;
import com.stripe.model.StripeError;
import com.stripe.model.Transfer;
import com.stripe.model.TransferReversalCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferReversalCollectionCreateParams;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the money-handling paths in PaymentService — authorization, 3DS, capture,
 * vendor transfer, and refund/cancel. Stripe's SDK calls (PaymentIntent, PaymentMethod,
 * Transfer, Refund) are all static factory methods, so they're stubbed via Mockito's
 * MockedStatic rather than passed in as mockable collaborators.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private NotificationService notificationService;
    @Mock private OutboxEventService outboxEventService;

    @InjectMocks
    private PaymentService paymentService;

    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        // `self` backs the REQUIRES_NEW notification write in failAndRecord() — in a
        // real app it's a Spring CGLIB proxy, but for a unit test calling straight
        // through to `this` is fine; propagation semantics aren't what's under test.
        ReflectionTestUtils.setField(paymentService, "self", paymentService);
        ReflectionTestUtils.setField(paymentService, "platformFeePercent", 10);
        ReflectionTestUtils.setField(paymentService, "connectRequired", true);

        User vendorUser = User.builder().publicUserId("VEND123").build();
        VendorProfile vendor = VendorProfile.builder()
                .user(vendorUser)
                .stripeAccountId("acct_vendor123")
                .build();

        User customerUser = User.builder().userId(42L).publicUserId("CUST123").build();
        CustomerProfile customer = CustomerProfile.builder().user(customerUser).build();

        order = Order.builder()
                .publicOrderId("ORD123")
                .subtotal(new BigDecimal("100.00"))
                .taxRate(new BigDecimal("0.05"))
                .totalAmount(new BigDecimal("105.00"))
                .vendor(vendor)
                .customer(customer)
                .build();

        payment = Payment.builder()
                .paymentId(1L)
                .order(order)
                .amount(new BigDecimal("105.00"))
                .status(PaymentStatus.PENDING)
                .build();
    }

    private PaymentIntent mockIntent(String status) {
        // lenient: not every test that calls this helper exercises all four
        // getters (e.g. some only check getStatus()), and Mockito's strict
        // stubbing would otherwise flag the unused ones as errors.
        PaymentIntent intent = mock(PaymentIntent.class);
        lenient().when(intent.getStatus()).thenReturn(status);
        lenient().when(intent.getId()).thenReturn("pi_123");
        lenient().when(intent.getPaymentMethod()).thenReturn("pm_123");
        lenient().when(intent.getClientSecret()).thenReturn("pi_123_secret_abc");
        return intent;
    }

    private PaymentMethod mockStripeCard() {
        PaymentMethod stripeMethod = mock(PaymentMethod.class);
        PaymentMethod.Card card = mock(PaymentMethod.Card.class);
        lenient().when(card.getLast4()).thenReturn("4242");
        lenient().when(card.getBrand()).thenReturn("visa");
        lenient().when(stripeMethod.getCard()).thenReturn(card);
        return stripeMethod;
    }

    // ========== chargeOrder ==========

    @Test
    void chargeOrder_success_authorizesAndSplitsFee() {
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));
        PaymentIntent intent = mockIntent("requires_capture");
        PaymentMethod stripeCard = mockStripeCard();

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class);
             MockedStatic<PaymentMethod> pmStatic = mockStatic(PaymentMethod.class)) {
            piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class))).thenReturn(intent);
            pmStatic.when(() -> PaymentMethod.retrieve("pm_123")).thenReturn(stripeCard);

            PaymentService.ChargeOutcome outcome = paymentService.chargeOrder(order, "pm_123");

            assertThat(outcome.isAuthorized()).isTrue();
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getTransactionId()).isEqualTo("pi_123");
        assertThat(payment.getCardLast4()).isEqualTo("4242");
        // subtotal $100, 10% platform fee => $10 fee, $90 to vendor
        assertThat(payment.getPlatformFeeAmount()).isEqualByComparingTo("10.00");
        assertThat(payment.getVendorPayout()).isEqualByComparingTo("90.00");
    }

    @Test
    void chargeOrder_requiresAction_leavesPaymentPendingWithClientSecret() {
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));
        PaymentIntent intent = mockIntent("requires_action");

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class))).thenReturn(intent);

            PaymentService.ChargeOutcome outcome = paymentService.chargeOrder(order, "pm_123");

            assertThat(outcome.requiresAction()).isTrue();
            assertThat(outcome.clientSecret()).isEqualTo("pi_123_secret_abc");
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getTransactionId()).isEqualTo("pi_123");
    }

    @Test
    void chargeOrder_stripeDecline_marksFailedAndThrows() {
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        // Mocked rather than constructed directly — Stripe's exception constructors
        // aren't part of the public contract worth pinning a test to; only the
        // getters PaymentService actually reads (getStripeError/getMessage) matter.
        StripeError error = mock(StripeError.class);
        when(error.getMessage()).thenReturn("Your card has insufficient funds.");
        CardException declined = mock(CardException.class);
        when(declined.getStripeError()).thenReturn(error);

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class))).thenThrow(declined);

            assertThatThrownBy(() -> paymentService.chargeOrder(order, "pm_123"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("insufficient funds");
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(outboxEventService).paymentFailed(eq("CUST123"), eq("ORD123"), any());
    }

    @Test
    void chargeOrder_paymentNotPending_throwsIllegalState() {
        payment.setStatus(PaymentStatus.AUTHORIZED);
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.chargeOrder(order, "pm_123"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void chargeOrder_existingIntent_skipsStripeCallAndReturnsCurrentStatus() {
        payment.setTransactionId("pi_already_exists");
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            PaymentService.ChargeOutcome outcome = paymentService.chargeOrder(order, "pm_123");

            assertThat(outcome.status()).isEqualTo(PaymentStatus.PENDING);
            piStatic.verify(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)), never());
        }
    }

    // ========== confirmAfter3ds ==========

    @Test
    void confirmAfter3ds_alreadyAuthorized_isNoOp() {
        payment.setStatus(PaymentStatus.AUTHORIZED);

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            PaymentService.ChargeOutcome outcome = paymentService.confirmAfter3ds(order, payment);

            assertThat(outcome.status()).isEqualTo(PaymentStatus.AUTHORIZED);
            piStatic.verify(() -> PaymentIntent.retrieve(any()), never());
        }
    }

    @Test
    void confirmAfter3ds_requiresCapture_authorizesAndFiresOrderPlacedEvents() {
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId("pi_123");
        PaymentIntent intent = mockIntent("requires_capture");
        PaymentMethod stripeCard = mockStripeCard();

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class);
             MockedStatic<PaymentMethod> pmStatic = mockStatic(PaymentMethod.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(intent);
            pmStatic.when(() -> PaymentMethod.retrieve("pm_123")).thenReturn(stripeCard);

            PaymentService.ChargeOutcome outcome = paymentService.confirmAfter3ds(order, payment);

            assertThat(outcome.isAuthorized()).isTrue();
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        verify(outboxEventService).orderPlaced("ORD123");
        verify(outboxEventService).customerOrderReceived("ORD123");
    }

    @Test
    void confirmAfter3ds_stillRequiresAction_returnsPendingWithSecret() {
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId("pi_123");
        PaymentIntent intent = mockIntent("requires_action");

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(intent);

            PaymentService.ChargeOutcome outcome = paymentService.confirmAfter3ds(order, payment);

            assertThat(outcome.requiresAction()).isTrue();
            assertThat(outcome.clientSecret()).isEqualTo("pi_123_secret_abc");
        }
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void confirmAfter3ds_intentCancelled_marksFailed() {
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId("pi_123");
        PaymentIntent intent = mockIntent("canceled");

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(intent);

            PaymentService.ChargeOutcome outcome = paymentService.confirmAfter3ds(order, payment);

            assertThat(outcome.isFailed()).isTrue();
        }
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // ========== captureStripePayment ==========

    @Test
    void captureStripePayment_success_completesPaymentAndFiresCapturedEvent() throws StripeException {
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setTransactionId("pi_123");
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        PaymentIntent intent = mock(PaymentIntent.class);
        PaymentIntent captured = mock(PaymentIntent.class);
        when(captured.getStatus()).thenReturn("succeeded");
        when(captured.getId()).thenReturn("pi_123");
        when(intent.capture(any(PaymentIntentCaptureParams.class), any(RequestOptions.class))).thenReturn(captured);

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(intent);

            paymentService.captureStripePayment(order, null);
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(outboxEventService).paymentCaptured(eq("CUST123"), eq("pi_123"), eq("ORD123"), any());
    }

    @Test
    void captureStripePayment_notAuthorized_throwsIllegalState() {
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.captureStripePayment(order, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void captureStripePayment_partialCapture_recalculatesFeeAndPayoutOnCapturedAmount() throws StripeException {
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setTransactionId("pi_123");
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        PaymentIntent intent = mock(PaymentIntent.class);
        PaymentIntent captured = mock(PaymentIntent.class);
        when(captured.getStatus()).thenReturn("succeeded");
        when(captured.getId()).thenReturn("pi_123");
        when(intent.capture(any(PaymentIntentCaptureParams.class), any(RequestOptions.class))).thenReturn(captured);

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(intent);

            // Original order total was $105 (incl. 5% tax on $100 subtotal); capture only $52.50
            // (an item substitution scenario) — fee/payout should be recomputed off the captured
            // amount's implied subtotal, not the original $100.
            paymentService.captureStripePayment(order, new BigDecimal("52.50"));
        }

        assertThat(payment.getAmount()).isEqualByComparingTo("52.50");
        // implied subtotal = 52.50 / 1.05 = 50.00, fee = 10% = 5.00, payout = 45.00
        assertThat(payment.getPlatformFeeAmount()).isEqualByComparingTo("5.00");
        assertThat(payment.getVendorPayout()).isEqualByComparingTo("45.00");
    }

    // ========== transferToVendor ==========

    @Test
    void transferToVendor_success_storesTransferId() {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId("pi_123");
        payment.setVendorPayout(new BigDecimal("90.00"));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getLatestCharge()).thenReturn("ch_123");
        Transfer transfer = mock(Transfer.class);
        when(transfer.getId()).thenReturn("tr_123");

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class);
             MockedStatic<Transfer> trStatic = mockStatic(Transfer.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(intent);
            trStatic.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class))).thenReturn(transfer);

            paymentService.transferToVendor(order);
        }

        assertThat(payment.getStripeTransferId()).isEqualTo("tr_123");
    }

    @Test
    void transferToVendor_alreadyTransferred_isIdempotentNoOp() {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setStripeTransferId("tr_existing");
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        try (MockedStatic<Transfer> trStatic = mockStatic(Transfer.class)) {
            paymentService.transferToVendor(order);
            trStatic.verify(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)), never());
        }
    }

    @Test
    void transferToVendor_paymentNotCompleted_throwsIllegalState() {
        payment.setStatus(PaymentStatus.AUTHORIZED);
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.transferToVendor(order))
                .isInstanceOf(IllegalStateException.class);
    }

    // ========== refundStripeCharge ==========

    @Test
    void refundStripeCharge_authorizedOnly_cancelsHoldNotRefund() {
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setTransactionId("pi_123");
        when(paymentRepository.findByOrderWithLock(order)).thenReturn(Optional.of(payment));

        PaymentIntent intent = mock(PaymentIntent.class);

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class);
             MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(intent);

            paymentService.refundStripeCharge(order);

            refundStatic.verify(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)), never());
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void refundStripeCharge_completedNoTransfer_refundsWithoutReversal() {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId("pi_123");
        payment.setStripeTransferId(null);
        when(paymentRepository.findByOrderWithLock(order)).thenReturn(Optional.of(payment));

        try (MockedStatic<Transfer> trStatic = mockStatic(Transfer.class);
             MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {
            refundStatic.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class))).thenReturn(mock(Refund.class));

            paymentService.refundStripeCharge(order);

            trStatic.verify(() -> Transfer.retrieve(any()), never());
            refundStatic.verify(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)));
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundStripeCharge_completedWithTransfer_reversesTransferThenRefunds() throws StripeException {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId("pi_123");
        payment.setStripeTransferId("tr_123");
        when(paymentRepository.findByOrderWithLock(order)).thenReturn(Optional.of(payment));

        Transfer transfer = mock(Transfer.class);
        TransferReversalCollection reversals = mock(TransferReversalCollection.class);
        when(transfer.getReversals()).thenReturn(reversals);

        try (MockedStatic<Transfer> trStatic = mockStatic(Transfer.class);
             MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {
            trStatic.when(() -> Transfer.retrieve("tr_123")).thenReturn(transfer);
            refundStatic.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class))).thenReturn(mock(Refund.class));

            paymentService.refundStripeCharge(order);

            verify(reversals).create(any(TransferReversalCollectionCreateParams.class), any(RequestOptions.class));
            refundStatic.verify(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)));
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundStripeCharge_noTransactionId_doesNothing() {
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId(null);
        when(paymentRepository.findByOrderWithLock(order)).thenReturn(Optional.of(payment));

        paymentService.refundStripeCharge(order);

        verify(paymentRepository, never()).save(any());
    }

    // ========== markPaymentCancelled ==========

    @Test
    void markPaymentCancelled_authorized_becomesCancelled() {
        payment.setStatus(PaymentStatus.AUTHORIZED);
        when(paymentRepository.findByOrderWithLock(order)).thenReturn(Optional.of(payment));

        paymentService.markPaymentCancelled(order);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void markPaymentCancelled_pendingAbandoned3ds_becomesCancelled() {
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByOrderWithLock(order)).thenReturn(Optional.of(payment));

        paymentService.markPaymentCancelled(order);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void markPaymentCancelled_alreadyTerminal_isNoOp() {
        payment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByOrderWithLock(order)).thenReturn(Optional.of(payment));

        paymentService.markPaymentCancelled(order);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, never()).save(any());
    }

    // ========== retryPayment ==========

    @Test
    void retryPayment_onlyAllowedOnFailedPayment() {
        payment.setStatus(PaymentStatus.PENDING);
        when(orderRepository.findByPublicOrderId("ORD123")).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() ->
                paymentService.retryPayment(userId(), "ORD123", "pm_new"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retryPayment_success_reAuthorizesAndFiresOrderPlacedEvents() {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setTransactionId("pi_dead");
        when(orderRepository.findByPublicOrderId("ORD123")).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        PaymentIntent intent = mockIntent("requires_capture");
        PaymentMethod stripeCard = mockStripeCard();

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class);
             MockedStatic<PaymentMethod> pmStatic = mockStatic(PaymentMethod.class)) {
            piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class))).thenReturn(intent);
            pmStatic.when(() -> PaymentMethod.retrieve("pm_123")).thenReturn(stripeCard);

            paymentService.retryPayment(userId(), "ORD123", "pm_123");
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        verify(outboxEventService, times(1)).orderPlaced("ORD123");
        verify(outboxEventService, times(1)).customerOrderReceived("ORD123");
    }

    @Test
    void retryPayment_blankPaymentMethod_throwsIllegalArgument() {
        when(orderRepository.findByPublicOrderId("ORD123")).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                paymentService.retryPayment(userId(), "ORD123", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========== refundPayment (admin) ==========

    @Test
    void refundPayment_notCompletedOrAuthorized_throwsIllegalState() {
        payment.setStatus(PaymentStatus.PENDING);
        when(orderRepository.findByPublicOrderId("ORD123")).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment("ORD123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot refund");
    }

    @Test
    void getPaymentByOrderId_wrongCustomer_throwsIllegalState() {
        when(orderRepository.findByPublicOrderId("ORD123")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.getPaymentByOrderId(999L, "ORD123"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getPaymentByOrderId_orderNotFound_throwsEntityNotFound() {
        when(orderRepository.findByPublicOrderId("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByOrderId(1L, "MISSING"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== helpers ==========

    private Long userId() {
        return order.getCustomer().getUser().getUserId();
    }
}
