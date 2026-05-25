package com.afrochow.outbox.service;

import com.afrochow.outbox.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Publishes committed outbox rows to Kafka.
 *
 * This service is intentionally small: it knows how to put an outbox row onto
 * its configured Kafka topic and wait for broker acknowledgement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.outbox.publish-timeout-seconds:10}")
    private long publishTimeoutSeconds;

    @Value("${app.kafka.topics.domain-events:afrochow.domain-events}")
    private String defaultDomainEventsTopic;

    public RecordMetadata publishToDefaultTopic(OutboxEvent event) {
        return publish(defaultDomainEventsTopic, defaultKey(event), event);
    }

    public RecordMetadata publish(OutboxEvent event) {
        String topic = event.getTopic() != null && !event.getTopic().isBlank()
                ? event.getTopic()
                : defaultDomainEventsTopic;
        return publish(topic, defaultKey(event), event);
    }

    public RecordMetadata publish(String topic, String key, OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                key != null && !key.isBlank() ? key : defaultKey(event),
                event.getPayload()
        );

        addHeader(record, "outbox-id", String.valueOf(event.getId()));
        addHeader(record, "outbox-event-id", event.getEventId());
        addHeader(record, "outbox-event-type", event.getEventType().name());
        addHeader(record, "outbox-aggregate-type", event.getAggregateType());
        addHeader(record, "outbox-aggregate-id", event.getAggregateId());
        addHeader(record, "outbox-created-at", String.valueOf(event.getCreatedAt()));

        try {
            RecordMetadata metadata = kafkaTemplate.send(record)
                    .get(publishTimeoutSeconds, TimeUnit.SECONDS)
                    .getRecordMetadata();

            log.info("outbox.kafka.published id={} type={} topic={} partition={} offset={}",
                    event.getId(), event.getEventType(), metadata.topic(),
                    metadata.partition(), metadata.offset());

            return metadata;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to publish outbox event " + event.getId() + " to Kafka topic " + topic,
                    ex
            );
        }
    }

    private String defaultKey(OutboxEvent event) {
        if (event.getAggregateType() != null && !event.getAggregateType().isBlank()
                && event.getAggregateId() != null && !event.getAggregateId().isBlank()) {
            return event.getAggregateType() + ":" + event.getAggregateId();
        }
        return event.getEventType().name() + ":" + event.getId();
    }

    private void addHeader(ProducerRecord<String, String> record, String key, String value) {
        if (value != null) {
            record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
