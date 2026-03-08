package com.afrochow.notification.dto;

import com.afrochow.common.enums.NotificationType;
import com.afrochow.common.enums.RelatedEntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private Long notificationId;
    private String title;
    private String message;
    private NotificationType type;
    private RelatedEntityType relatedEntityType;
    private String relatedEntityId; // Order ID, Payment ID, etc.
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private String userName;
}
