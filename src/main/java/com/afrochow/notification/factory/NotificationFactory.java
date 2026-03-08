package com.afrochow.notification.factory;

import com.afrochow.notification.model.Notification;
import com.afrochow.payment.model.Payment;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.NotificationType;
import com.afrochow.common.enums.RelatedEntityType;

import java.time.LocalDateTime;

public class NotificationFactory {

    public static Notification createPaymentNotification(User user, Payment payment) {
        return Notification.builder()
                .title("Payment Successful")
                .message("Payment for Order #" + payment.publicOrderId() + " completed")
                .type(NotificationType.PAYMENT_SUCCESS)
                .relatedEntityType(RelatedEntityType.PAYMENT)
                .relatedEntityId(payment.getPaymentId().toString())
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static Notification createOrderUpdateNotification(User user, String orderId, String status) {
        return Notification.builder()
                .title("Order Update")
                .message("Order #" + orderId + " is now " + status)
                .type(NotificationType.ORDER_UPDATE)
                .relatedEntityType(RelatedEntityType.ORDER)
                .relatedEntityId(orderId)
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // Add more helpers as needed
}
