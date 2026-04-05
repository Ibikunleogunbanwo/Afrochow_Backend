package com.afrochow.outbox.model;

import com.afrochow.outbox.enums.OutboxEventType;
import com.afrochow.outbox.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persistent outbox record.
 *
 * Written in the same DB transaction as the business state change, ensuring
 * the event is never lost even if the app crashes between the commit and the
 * notification dispatch.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private OutboxEventType eventType;

    /**
     * JSON payload — contains only public IDs and primitive values.
     * Deserialized by OutboxPoller into a Map<String, String> for dispatch.
     */
    @Column(columnDefinition = "JSON", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
