package com.afrochow.notification.model;

import com.afrochow.common.enums.NotificationType;
import com.afrochow.common.enums.RelatedEntityType;
import com.afrochow.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    private String title;

    /**
     * Explicit TEXT column — plain "private String message" with no @Column would
     * let Hibernate default it to VARCHAR(255). That's what actually happened in
     * this dev DB (the notification table predates/bypasses Flyway management),
     * and it silently truncated/crashed on broadcast messages up to the frontend's
     * allowed 500 chars: broadcastNotification() is @Async, so the admin's HTTP
     * request already returned "sent successfully" before the background batch
     * insert threw DataIntegrityViolationException — a broadcast could report
     * success while notifying zero users. See V33 migration, which widens the
     * actual column to match (required in prod, where ddl-auto=validate means
     * Hibernate won't just auto-fix the drift the way dev's ddl-auto=update does).
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    private RelatedEntityType relatedEntityType;

    private String relatedEntityId; // Order ID, Payment ID, etc.

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    private LocalDateTime readAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // ========== HELPER METHODS ==========

    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    public void markAsUnread() {
        this.isRead = false;
        this.readAt = null;
    }
}
