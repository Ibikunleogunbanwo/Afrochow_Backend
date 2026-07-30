package com.afrochow.notification.service;

import com.afrochow.kafka.service.ProcessedKafkaEventService;
import com.afrochow.outbox.enums.OutboxEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock private NotificationService notificationService;
    @Mock private ProcessedKafkaEventService processedKafkaEventService;
    @Mock private Acknowledgment acknowledgment;

    private NotificationEventConsumer consumer;

    private static final String CONSUMER_NAME = "afrochow-notification-service";

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(notificationService, processedKafkaEventService);
        ReflectionTestUtils.setField(consumer, "consumerName", CONSUMER_NAME);
        lenient().when(processedKafkaEventService.alreadyProcessed(eq(CONSUMER_NAME), anyString())).thenReturn(false);
    }

    private void consume(String payload, String outboxId, String eventId, String eventType) throws Exception {
        consumer.consume(payload, outboxId, eventId, eventType, acknowledgment);
    }

    // ========== dispatch routing ==========

    @Test
    void orderPlaced_dispatchesToNotifyVendorNewOrder() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "ORDER_PLACED");

        verify(notificationService).notifyVendorNewOrder("AFC-0001");
        verify(acknowledgment).acknowledge();
        verify(processedKafkaEventService).markProcessed(CONSUMER_NAME, "e1", "o1", "ORDER_PLACED");
    }

    @Test
    void customerOrderReceived_dispatchesCorrectly() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "CUSTOMER_ORDER_RECEIVED");

        verify(notificationService).notifyCustomerOrderReceived("AFC-0001");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void orderConfirmed_dispatchesCorrectly() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "ORDER_CONFIRMED");

        verify(notificationService).notifyCustomerOrderConfirmed("AFC-0001");
    }

    @Test
    void orderCancelled_dispatchesWithAllFields() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\",\"reason\":\"Out of stock\",\"previousStatus\":\"PENDING\",\"cancelledBy\":\"VENDOR\"}",
                "o1", "e1", "ORDER_CANCELLED");

        verify(notificationService).notifyCustomerOrderCancelled("AFC-0001", "Out of stock", "PENDING", "VENDOR");
    }

    @Test
    void orderCancelled_missingOptionalFields_passesNullsThrough() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "ORDER_CANCELLED");

        verify(notificationService).notifyCustomerOrderCancelled("AFC-0001", null, null, null);
    }

    @Test
    void orderPreparing_dispatchesCorrectly() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "ORDER_PREPARING");
        verify(notificationService).notifyCustomerOrderPreparing("AFC-0001");
    }

    @Test
    void orderReady_dispatchesCorrectly() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "ORDER_READY");
        verify(notificationService).notifyCustomerOrderReady("AFC-0001");
    }

    @Test
    void orderOutForDelivery_dispatchesCorrectly() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "ORDER_OUT_FOR_DELIVERY");
        verify(notificationService).notifyCustomerOrderOutForDelivery("AFC-0001");
    }

    @Test
    void orderDelivered_dispatchesCorrectly() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "ORDER_DELIVERED");
        verify(notificationService).notifyCustomerOrderDelivered("AFC-0001");
    }

    @Test
    void orderFulfillmentOverdue_dispatchesCorrectly() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "ORDER_FULFILLMENT_OVERDUE");
        verify(notificationService).notifyVendorAndAdminsOrderOverdue("AFC-0001");
    }

    @Test
    void paymentCaptured_parsesAmountAsBigDecimal() throws Exception {
        consume("{\"userPublicId\":\"CUS1\",\"paymentId\":\"PAY1\",\"publicOrderId\":\"AFC-0001\",\"amount\":\"42.50\"}",
                "o1", "e1", "PAYMENT_CAPTURED");

        verify(notificationService).notifyPaymentSuccess("CUS1", "PAY1", "AFC-0001", new BigDecimal("42.50"));
    }

    @Test
    void paymentFailed_dispatchesCorrectly() throws Exception {
        consume("{\"userPublicId\":\"CUS1\",\"publicOrderId\":\"AFC-0001\",\"reason\":\"Declined\"}",
                "o1", "e1", "PAYMENT_FAILED");

        verify(notificationService).notifyPaymentFailed("CUS1", "AFC-0001", "Declined");
    }

    @Test
    void vendorReviewed_parsesRatingAsInt() throws Exception {
        consume("{\"vendorPublicId\":\"VEN1\",\"reviewerName\":\"Ade\",\"rating\":\"5\",\"reviewType\":\"restaurant\"}",
                "o1", "e1", "VENDOR_REVIEWED");

        verify(notificationService).notifyVendorNewReview("VEN1", "Ade", 5, "restaurant");
    }

    @Test
    void vendorFavourited_dispatchesCorrectly() throws Exception {
        consume("{\"vendorPublicId\":\"VEN1\",\"customerName\":\"Ade\"}", "o1", "e1", "VENDOR_FAVOURITED");
        verify(notificationService).notifyVendorFavorited("VEN1", "Ade");
    }

    @Test
    void vendorCustomerCancelled_dispatchesCorrectly() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "VENDOR_CUSTOMER_CANCELLED");
        verify(notificationService).notifyVendorCustomerCancelled("AFC-0001");
    }

    @Test
    void vendorUnableToFulfil_dispatchesCorrectly() throws Exception {
        consume("{\"publicOrderId\":\"AFC-0001\",\"reason\":\"No stock\"}", "o1", "e1", "VENDOR_UNABLE_TO_FULFIL");
        verify(notificationService).notifyCustomerVendorUnableToFulfil("AFC-0001", "No stock");
    }

    @Test
    void userRegistered_dispatchesCorrectly() throws Exception {
        consume("{\"publicUserId\":\"CUS1\",\"email\":\"c@example.com\",\"firstName\":\"Ade\",\"role\":\"CUSTOMER\"}",
                "o1", "e1", "USER_REGISTERED");
        verify(notificationService).notifyUserRegistered("CUS1", "c@example.com", "Ade", "CUSTOMER");
    }

    @Test
    void passwordChanged_dispatchesCorrectly() throws Exception {
        consume("{\"publicUserId\":\"CUS1\",\"email\":\"c@example.com\",\"firstName\":\"Ade\"}",
                "o1", "e1", "PASSWORD_CHANGED");
        verify(notificationService).notifyPasswordChanged("CUS1", "c@example.com", "Ade");
    }

    @Test
    void passwordResetRequested_dispatchesCorrectly() throws Exception {
        consume("{\"publicUserId\":\"CUS1\",\"email\":\"c@example.com\",\"firstName\":\"Ade\",\"resetLink\":\"https://x/reset\"}",
                "o1", "e1", "PASSWORD_RESET_REQUESTED");
        verify(notificationService).notifyPasswordResetRequested("CUS1", "c@example.com", "Ade", "https://x/reset");
    }

    @Test
    void emailVerificationSent_dispatchesCorrectly() throws Exception {
        consume("{\"publicUserId\":\"CUS1\",\"email\":\"c@example.com\",\"firstName\":\"Ade\",\"verificationToken\":\"123456\"}",
                "o1", "e1", "EMAIL_VERIFICATION_SENT");
        verify(notificationService).notifyEmailVerificationSent("CUS1", "c@example.com", "Ade", "123456");
    }

    @Test
    void accountDeletionRequested_dispatchesCorrectly() throws Exception {
        consume("{\"publicUserId\":\"CUS1\",\"email\":\"c@example.com\",\"firstName\":\"Ade\"}",
                "o1", "e1", "ACCOUNT_DELETION_REQUESTED");
        verify(notificationService).notifyAccountDeletionRequested("CUS1", "c@example.com", "Ade");
    }

    @Test
    void vendorProvisional_dispatchesCorrectly() throws Exception {
        consume("{\"email\":\"v@example.com\",\"firstName\":\"Vendy\",\"restaurantName\":\"Jollof House\"}",
                "o1", "e1", "VENDOR_PROVISIONAL");
        verify(notificationService).notifyVendorProvisional("v@example.com", "Vendy", "Jollof House");
    }

    @Test
    void vendorCertificateUploaded_dispatchesCorrectly() throws Exception {
        consume("{\"publicVendorId\":\"VEN1\",\"publicUserId\":\"USR1\",\"restaurantName\":\"Jollof House\",\"certificateUrl\":\"https://cdn/cert.pdf\"}",
                "o1", "e1", "VENDOR_CERTIFICATE_UPLOADED");
        verify(notificationService).notifyAdminsVendorCertificateUploaded(
                "VEN1", "USR1", "Jollof House", "https://cdn/cert.pdf");
    }

    @Test
    void vendorApproved_dispatchesCorrectly() throws Exception {
        consume("{\"email\":\"v@example.com\",\"firstName\":\"Vendy\",\"restaurantName\":\"Jollof House\"}",
                "o1", "e1", "VENDOR_APPROVED");
        verify(notificationService).notifyVendorApproved("v@example.com", "Vendy", "Jollof House");
    }

    @Test
    void vendorRejected_dispatchesCorrectly() throws Exception {
        consume("{\"email\":\"v@example.com\",\"firstName\":\"Vendy\",\"restaurantName\":\"Jollof House\",\"reason\":\"Incomplete docs\"}",
                "o1", "e1", "VENDOR_REJECTED");
        verify(notificationService).notifyVendorRejected("v@example.com", "Vendy", "Jollof House", "Incomplete docs");
    }

    @Test
    void vendorSuspended_dispatchesCorrectly() throws Exception {
        consume("{\"email\":\"v@example.com\",\"firstName\":\"Vendy\",\"restaurantName\":\"Jollof House\"}",
                "o1", "e1", "VENDOR_SUSPENDED");
        verify(notificationService).notifyVendorSuspended("v@example.com", "Vendy", "Jollof House");
    }

    @Test
    void vendorReinstated_dispatchesCorrectly() throws Exception {
        consume("{\"email\":\"v@example.com\",\"firstName\":\"Vendy\",\"restaurantName\":\"Jollof House\"}",
                "o1", "e1", "VENDOR_REINSTATED");
        verify(notificationService).notifyVendorReinstated("v@example.com", "Vendy", "Jollof House");
    }

    @Test
    void broadcastSent_dispatchesToProcessBroadcast() throws Exception {
        consume("{\"title\":\"Sale\",\"message\":\"50% off\",\"type\":\"PROMO\",\"targetAudience\":\"ALL\",\"sentBy\":\"admin1\"}",
                "o1", "e1", "BROADCAST_SENT");
        verify(notificationService).processBroadcast("Sale", "50% off", "PROMO", "ALL", "admin1");
    }

    @Test
    void waitlistJoined_dispatchesCorrectly() throws Exception {
        consume("{\"email\":\"w@example.com\",\"name\":\"Wendy\",\"role\":\"CUSTOMER\"}",
                "o1", "e1", "WAITLIST_JOINED");
        verify(notificationService).notifyWaitlistJoined("w@example.com", "Wendy", "CUSTOMER");
    }

    // ========== unsupported types ==========

    @Test
    void unsupportedType_addressGeocodingRequested_skipsAndAcknowledges() throws Exception {
        consume("{}", "o1", "e1", "ADDRESS_GEOCODING_REQUESTED");

        verifyNoInteractions(notificationService);
        verify(processedKafkaEventService, never()).alreadyProcessed(any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void unsupportedType_paymentTransferRequested_skipsAndAcknowledges() throws Exception {
        consume("{}", "o1", "e1", "PAYMENT_TRANSFER_REQUESTED");

        verifyNoInteractions(notificationService);
        verify(acknowledgment).acknowledge();
    }

    // ========== dedup ==========

    @Test
    void alreadyProcessedEvent_skipsDispatchButAcknowledges() throws Exception {
        when(processedKafkaEventService.alreadyProcessed(CONSUMER_NAME, "e1")).thenReturn(true);

        consume("{\"publicOrderId\":\"AFC-0001\"}", "o1", "e1", "ORDER_PLACED");

        verify(notificationService, never()).notifyVendorNewOrder(any());
        verify(acknowledgment).acknowledge();
        verify(processedKafkaEventService, never()).markProcessed(any(), any(), any(), any());
    }

    // ========== malformed / missing payload fields ==========

    @Test
    void missingRequiredField_throwsAndDoesNotAcknowledge() {
        assertThatThrownBy(() -> consume("{}", "o1", "e1", "ORDER_PLACED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publicOrderId");

        verify(acknowledgment, never()).acknowledge();
        verify(processedKafkaEventService, never()).markProcessed(any(), any(), any(), any());
    }

    @Test
    void blankRequiredField_treatedAsMissing() {
        assertThatThrownBy(() -> consume("{\"publicOrderId\":\"\"}", "o1", "e1", "ORDER_PLACED"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
