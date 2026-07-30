package com.afrochow.notification.service;

import com.afrochow.common.enums.NotificationType;
import com.afrochow.common.enums.RelatedEntityType;
import com.afrochow.common.enums.Role;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.email.EmailService;
import com.afrochow.notification.dto.BroadcastNotificationRequestDto;
import com.afrochow.notification.dto.NotificationDto;
import com.afrochow.notification.model.Notification;
import com.afrochow.notification.repository.BroadcastLogRepository;
import com.afrochow.notification.repository.NotificationRepository;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private BroadcastLogRepository broadcastLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private EmailService emailService;
    @Mock private OutboxEventService outboxEventService;

    @InjectMocks private NotificationService notificationService;

    private User customer;
    private User vendorUser;
    private VendorProfile vendor;
    private CustomerProfile customerProfile;
    private Order order;

    @BeforeEach
    void setUp() {
        customerProfile = CustomerProfile.builder().notificationsEnabled(true).build();
        customer = User.builder().userId(1L).publicUserId("CUS1").username("adecustomer")
                .email("customer@example.com").firstName("Ade").lastName("Customer")
                .role(Role.CUSTOMER).customerProfile(customerProfile).build();
        customerProfile.setUser(customer);
        vendorUser = User.builder().userId(2L).publicUserId("VEN1").username("jollofhouse")
                .email("vendor@example.com").firstName("Jollof").lastName("House")
                .role(Role.VENDOR).build();
        vendor = VendorProfile.builder().id(5L).user(vendorUser).restaurantName("Jollof House").build();
        order = Order.builder().publicOrderId("AFC-0001").customer(customerProfile).vendor(vendor)
                .totalAmount(new BigDecimal("42.00")).createdAt(LocalDateTime.now())
                .fulfillmentType("DELIVERY").status(com.afrochow.common.enums.OrderStatus.READY_FOR_PICKUP)
                .build();
    }

    // ========== generic create ==========

    @Test
    void createNotification_savesAndReturnsDto() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setNotificationId(100L);
            return n;
        });

        NotificationDto result = notificationService.createNotification("CUS1", "Title", "Message",
                NotificationType.SYSTEM_ALERT, null, null);

        assertThat(result.getTitle()).isEqualTo("Title");
        assertThat(result.getUserName()).isEqualTo("Ade Customer");
    }

    @Test
    void createNotification_userNotFound_throwsEntityNotFound() {
        when(userRepository.findByPublicUserId("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.createNotification("ghost", "t", "m",
                NotificationType.SYSTEM_ALERT, null, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== order lifecycle ==========

    @Test
    void notifyCustomerOrderConfirmed_sendsInAppAndEmail() {
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));

        notificationService.notifyCustomerOrderConfirmed("AFC-0001");

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService).sendOrderConfirmationEmail(
                eq("customer@example.com"), eq("Ade"), eq("AFC-0001"), eq("Jollof House"),
                eq(new BigDecimal("42.00")), any());
    }

    @Test
    void notifyCustomerOrderConfirmed_optedOutCustomer_skipsNotification() {
        customerProfile.setNotificationsEnabled(false);
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));

        notificationService.notifyCustomerOrderConfirmed("AFC-0001");

        verify(notificationRepository, never()).save(any());
        verify(emailService, never()).sendOrderConfirmationEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void notifyCustomerOrderConfirmed_orderNotFound_noOpNoThrow() {
        when(orderRepository.findByPublicOrderId("missing")).thenReturn(Optional.empty());

        notificationService.notifyCustomerOrderConfirmed("missing");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyCustomerOrderConfirmed_emailFails_wrapsInIllegalState() {
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("SMTP down")).when(emailService)
                .sendOrderConfirmationEmail(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> notificationService.notifyCustomerOrderConfirmed("AFC-0001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Notification dispatch failed");
    }

    @Test
    void notifyVendorNewOrder_usesNewOrderType() {
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyVendorNewOrder("AFC-0001");

        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.NEW_ORDER);
        assertThat(captor.getValue().getUser()).isEqualTo(vendorUser);
        verify(emailService).sendNewOrderNotificationToVendor(
                eq("vendor@example.com"), eq("Jollof House"), eq("AFC-0001"),
                eq("Ade Customer"), eq(new BigDecimal("42.00")));
    }

    @Test
    void notifyCustomerOrderReady_pickupOrder_usesPickupMessage() {
        order.setFulfillmentType("PICKUP");
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyCustomerOrderReady("AFC-0001");

        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("ready for pickup");
    }

    @Test
    void notifyCustomerOrderCancelled_systemCancellation_mentionsHoldRelease() {
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyCustomerOrderCancelled("AFC-0001", null, "PENDING", "SYSTEM");

        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Order Automatically Cancelled");
        assertThat(captor.getValue().getMessage()).contains("not been charged");
    }

    @Test
    void notifyCustomerOrderCancelled_systemOverdueCancellation_mentionsRefund() {
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyCustomerOrderCancelled("AFC-0001", null, "CONFIRMED", "SYSTEM_OVERDUE");

        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Order Automatically Cancelled and Refunded");
        assertThat(captor.getValue().getMessage()).contains("refunded");
    }

    @Test
    void notifyCustomerOrderCancelled_vendorCancellation_includesReason() {
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyCustomerOrderCancelled("AFC-0001", "Out of stock", "PENDING", "VENDOR");

        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Order Declined by Restaurant");
        assertThat(captor.getValue().getMessage()).contains("Out of stock");
    }

    @Test
    void notifyCustomerOrderCancelled_adminCancellation_mentionsSupport() {
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyCustomerOrderCancelled("AFC-0001", "Policy violation", "PENDING", "ADMIN");

        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("Afrochow support");
    }

    @Test
    void notifyCustomerOrderCancelled_defaultCancelledBy_usesGenericMessage() {
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyCustomerOrderCancelled("AFC-0001", null, "PENDING", null);

        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Order Cancelled");
    }

    // ========== payment notifications ==========

    @Test
    void notifyPaymentSuccess_sendsInAppAndEmail() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));

        notificationService.notifyPaymentSuccess("CUS1", "PAY1", "AFC-0001", new BigDecimal("42.00"));

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService).sendPaymentConfirmationEmail(
                "customer@example.com", "Ade", "PAY1", "AFC-0001", new BigDecimal("42.00"));
    }

    @Test
    void notifyPaymentSuccess_optedOutCustomer_skipsEntirely() {
        customerProfile.setNotificationsEnabled(false);
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));

        notificationService.notifyPaymentSuccess("CUS1", "PAY1", "AFC-0001", new BigDecimal("42.00"));

        verify(notificationRepository, never()).save(any());
        verify(emailService, never()).sendPaymentConfirmationEmail(any(), any(), any(), any(), any());
    }

    @Test
    void notifyPaymentFailed_orderExists_sendsInAppAndEmailWithRetry() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.of(order));

        notificationService.notifyPaymentFailed("CUS1", "AFC-0001", "Card declined");

        verify(emailService).sendPaymentFailedEmail("customer@example.com", "Ade", "AFC-0001", "Card declined", true);
    }

    @Test
    void notifyPaymentFailed_orderNeverPersisted_sendsEmailWithoutRetryLink() {
        // The initial chargeOrder() decline rolls back the whole order — nothing to
        // retry against, so the email/notification must not claim otherwise or link
        // to an order-confirmation page that will 404.
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(orderRepository.findByPublicOrderId("AFC-0001")).thenReturn(Optional.empty());

        notificationService.notifyPaymentFailed("CUS1", "AFC-0001", "Card declined");

        verify(emailService).sendPaymentFailedEmail("customer@example.com", "Ade", "AFC-0001", "Card declined", false);
    }

    // ========== review / favorite ==========

    @Test
    void notifyVendorNewReview_createsNotificationWithStars() {
        when(userRepository.findByPublicUserId("VEN1")).thenReturn(Optional.of(vendorUser));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyVendorNewReview("VEN1", "Ade Customer", 5, "restaurant");

        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("5-star").contains("⭐⭐⭐⭐⭐");
    }

    @Test
    void notifyVendorFavorited_createsNotification() {
        when(userRepository.findByPublicUserId("VEN1")).thenReturn(Optional.of(vendorUser));

        notificationService.notifyVendorFavorited("VEN1", "Ade Customer");

        verify(notificationRepository).save(any(Notification.class));
    }

    // ========== broadcast producer/consumer ==========

    @Test
    void enqueueBroadcast_writesOutboxEvent() {
        BroadcastNotificationRequestDto dto = BroadcastNotificationRequestDto.builder()
                .title("Sale!").message("50% off today").type(NotificationType.PROMO)
                .targetAudience(BroadcastNotificationRequestDto.TargetAudience.CUSTOMERS).build();

        notificationService.enqueueBroadcast(dto, "admin1");

        verify(outboxEventService).broadcastSent("Sale!", "50% off today", "PROMO", "CUSTOMERS", "admin1");
    }

    @Test
    void processBroadcast_customersAudience_savesNotificationsAndLog() {
        User optedOutCustomer = User.builder().userId(3L).publicUserId("CUS2")
                .email("optedout@example.com").firstName("Opted").lastName("Out")
                .role(Role.CUSTOMER)
                .customerProfile(CustomerProfile.builder().notificationsEnabled(false).build())
                .build();
        Page<User> page = new PageImpl<>(List.of(customer, optedOutCustomer));
        when(userRepository.countByRole(Role.CUSTOMER)).thenReturn(2L);
        when(userRepository.findAllByRole(eq(Role.CUSTOMER), any(Pageable.class))).thenReturn(page);

        notificationService.processBroadcast("Sale!", "50% off", "PROMO", "CUSTOMERS", "admin1");

        ArgumentCaptor<List<Notification>> notifCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(notifCaptor.capture());
        assertThat(notifCaptor.getValue()).hasSize(1); // opted-out customer excluded
        verify(emailService).sendNotificationEmail("customer@example.com", "Ade", "Sale!", "50% off");
        verify(emailService, never()).sendNotificationEmail(eq("optedout@example.com"), any(), any(), any());
        verify(broadcastLogRepository).save(argThat(log ->
                log.getRecipientCount() == 2 && log.getTargetAudience().equals("CUSTOMERS")));
    }

    @Test
    void processBroadcast_oneEmailFails_doesNotAbortRemainingRecipients() {
        Page<User> page = new PageImpl<>(List.of(customer));
        when(userRepository.countByRole(Role.CUSTOMER)).thenReturn(1L);
        when(userRepository.findAllByRole(eq(Role.CUSTOMER), any(Pageable.class))).thenReturn(page);
        doThrow(new RuntimeException("bounced")).when(emailService)
                .sendNotificationEmail(anyString(), anyString(), anyString(), anyString());

        // Should not throw despite the email failure — broadcast email is best-effort.
        notificationService.processBroadcast("Sale!", "50% off", "PROMO", "CUSTOMERS", "admin1");

        verify(notificationRepository).saveAll(any());
        verify(broadcastLogRepository).save(any());
    }

    // ========== read ==========

    @Test
    void getUserNotifications_returnsPagedDtos() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        Notification n = Notification.builder().notificationId(1L).user(customer)
                .title("t").message("m").isRead(false).build();
        Page<Notification> page = new PageImpl<>(List.of(n));
        when(notificationRepository.findByUserOrderByCreatedAtDesc(eq(customer), any(Pageable.class)))
                .thenReturn(page);

        Page<NotificationDto> result = notificationService.getUserNotifications("CUS1", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getUnreadNotifications_returnsOnlyUnread() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(notificationRepository.findByUserAndIsReadOrderByCreatedAtDesc(customer, false))
                .thenReturn(List.of(Notification.builder().notificationId(1L).isRead(false).build()));

        List<NotificationDto> result = notificationService.getUnreadNotifications("CUS1");

        assertThat(result).hasSize(1);
    }

    // ========== update ==========

    @Test
    void markAsRead_ownedNotification_marksRead() {
        Notification n = Notification.builder().notificationId(1L).user(customer).isRead(false).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto result = notificationService.markAsRead("CUS1", 1L);

        assertThat(result.getIsRead()).isTrue();
    }

    @Test
    void markAsRead_notOwner_throwsIllegalState() {
        Notification n = Notification.builder().notificationId(1L).user(customer).isRead(false).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markAsRead("someoneelse@example.com", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("your own notifications");
    }

    @Test
    void markAsUnread_ownedNotification_marksUnread() {
        Notification n = Notification.builder().notificationId(1L).user(customer).isRead(true)
                .readAt(LocalDateTime.now()).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto result = notificationService.markAsUnread("CUS1", 1L);

        assertThat(result.getIsRead()).isFalse();
    }

    @Test
    void markAllAsRead_delegatesToRepository() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));

        notificationService.markAllAsRead("CUS1");

        verify(notificationRepository).markAllAsReadByUser(eq(customer), any(LocalDateTime.class));
    }

    // ========== delete ==========

    @Test
    void deleteNotification_ownedNotification_deletes() {
        Notification n = Notification.builder().notificationId(1L).user(customer).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        notificationService.deleteNotification("CUS1", 1L);

        verify(notificationRepository).delete(n);
    }

    @Test
    void deleteAllReadNotifications_delegatesToRepository() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));

        notificationService.deleteAllReadNotifications("CUS1");

        verify(notificationRepository).deleteAllReadByUser(customer);
    }

    // ========== stats ==========

    @Test
    void getNotificationStats_computesReadAsDifference() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(notificationRepository.countByUser(customer)).thenReturn(10L);
        when(notificationRepository.countByUserAndIsRead(customer, false)).thenReturn(3L);

        NotificationService.NotificationStats stats = notificationService.getNotificationStats("CUS1");

        assertThat(stats.getTotalNotifications()).isEqualTo(10L);
        assertThat(stats.getUnreadNotifications()).isEqualTo(3L);
        assertThat(stats.getReadNotifications()).isEqualTo(7L);
    }

    // ========== auth/account lifecycle (email-driven) ==========

    @Test
    void notifyUserRegistered_customerRole_sendsWelcomeEmailAndInAppNotification() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyUserRegistered("CUS1", "customer@example.com", "Ade", "CUSTOMER");

        verify(emailService).sendWelcomeEmail("customer@example.com", "Ade", "CUSTOMER");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyUserRegistered_emailFails_throwsAndSkipsInAppNotification() {
        doThrow(new RuntimeException("SMTP down")).when(emailService)
                .sendWelcomeEmail(any(), any(), any());

        assertThatThrownBy(() -> notificationService.notifyUserRegistered("CUS1", "customer@example.com", "Ade", "CUSTOMER"))
                .isInstanceOf(IllegalStateException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyPasswordChanged_sendsEmailAndInAppNotification() {
        when(userRepository.findByPublicUserId("CUS1")).thenReturn(Optional.of(customer));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyPasswordChanged("CUS1", "customer@example.com", "Ade");

        verify(emailService).sendPasswordChangedEmail("customer@example.com", "Ade");
        verify(notificationRepository).save(any(Notification.class));
    }
}
