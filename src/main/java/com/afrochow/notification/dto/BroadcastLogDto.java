package com.afrochow.notification.dto;

import com.afrochow.common.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BroadcastLogDto {

    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private String targetAudience;
    private int recipientCount;
    private LocalDateTime sentAt;
    private String sentBy;
}
