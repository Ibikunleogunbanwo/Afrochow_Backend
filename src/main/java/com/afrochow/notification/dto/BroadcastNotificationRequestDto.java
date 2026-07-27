package com.afrochow.notification.dto;

import com.afrochow.common.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastNotificationRequestDto {

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;

    // Matches the frontend textarea's maxLength — the notification.message
    // column is TEXT (effectively unbounded) as of V33, but bounding this at
    // the API layer keeps the contract explicit rather than relying solely on
    // "whatever fits in the column," and stops a direct API call from sending
    // an unreasonably large broadcast to every user's notification feed.
    @NotBlank(message = "Message is required")
    @Size(max = 500, message = "Message cannot exceed 500 characters")
    private String message;

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotNull
    @Builder.Default
    private TargetAudience targetAudience = TargetAudience.ALL;

    public enum TargetAudience {
        ALL, CUSTOMERS, VENDORS
    }
}
