package com.afrochow.kafka.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "processed_kafka_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processed_kafka_event_consumer_event",
                columnNames = {"consumer_name", "event_id"}
        ),
        indexes = {
                @Index(name = "idx_processed_kafka_event_event_id", columnList = "event_id"),
                @Index(name = "idx_processed_kafka_event_consumer_processed_at", columnList = "consumer_name, processed_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedKafkaEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_name", nullable = false, length = 120)
    private String consumerName;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "outbox_id", length = 40)
    private String outboxId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();
}
